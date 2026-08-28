package rs.moma.janus.kredenac.routes

import rs.moma.janus.kredenac.crypto.authentication.RefreshTokenService
import rs.moma.janus.kredenac.crypto.authentication.MagicLinkService
import rs.moma.janus.kredenac.crypto.webauthn.verifyRegistration
import rs.moma.janus.kredenac.crypto.authentication.CsrfService
import rs.moma.janus.kredenac.crypto.authentication.JwtService
import rs.moma.janus.kredenac.crypto.webauthn.WebAuthnService
import rs.moma.janus.kredenac.dtos.AddCredentialFinishRequest
import rs.moma.janus.kredenac.plugins.authChallengeRateLimit
import rs.moma.janus.kredenac.common.UnauthorizedException
import rs.moma.janus.kredenac.crypto.webauthn.LoginOutcome
import rs.moma.janus.kredenac.plugins.authRefreshRateLimit
import rs.moma.janus.kredenac.crypto.webauthn.verifyLogin
import rs.moma.janus.kredenac.plugins.authenticatedDelete
import rs.moma.janus.kredenac.dtos.RegisterFinishRequest
import rs.moma.janus.kredenac.dtos.RegisterVerifyRequest
import rs.moma.janus.kredenac.plugins.magicLinkRateLimit
import rs.moma.janus.kredenac.plugins.authenticatedPost
import rs.moma.janus.kredenac.plugins.authenticatedGet
import rs.moma.janus.kredenac.dtos.LoginFinishRequest
import rs.moma.janus.kredenac.services.UserService
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.util.getOrFail
import org.koin.core.qualifier.named
import io.ktor.server.application.*
import io.ktor.util.date.GMTDate
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import io.ktor.http.*

fun Route.authRoutes() {
    val refreshTokenService: RefreshTokenService by inject()
    val magicLinkService: MagicLinkService by inject()
    val webAuthnService: WebAuthnService by inject()
    val rpId: String by inject(named("rpId"))
    val userService: UserService by inject()
    val csrfService: CsrfService by inject()
    val jwtService: JwtService by inject()

    suspend fun startChallenge(call: ApplicationCall) {
        val session = webAuthnService.start()
        call.setCookie("challenge_session", session.cookie, "/auth")
        call.respond(mapOf("challenge" to session.challenge, "rpId" to rpId))
    }

    route("/auth") {
        rateLimit(magicLinkRateLimit) {
            post("/register/verify") {
                val request = call.receive<RegisterVerifyRequest>()
                magicLinkService.request(request.email)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        rateLimit(authChallengeRateLimit) {
            post("/register/start") { startChallenge(call) }
            post("/register/finish") {
                val cookie = call.request.cookies["challenge_session"]
                val request = call.receive<RegisterFinishRequest>()
                val attestationObject = request.attestationObject
                val clientDataJSON = request.clientDataJSON

                val parsed = webAuthnService.verifyRegistration(clientDataJSON, attestationObject, cookie)
                val email = magicLinkService.getEmail(request.token)
                userService.register(email, parsed.credentialId, parsed.algorithm, parsed.publicKey)

                call.clearChallengeSessionCookie()
                call.respond(HttpStatusCode.Created)
            }

            post("/login/start") { startChallenge(call) }
            post("/login/finish") {
                val request = call.receive<LoginFinishRequest>()
                val cookie = call.request.cookies["challenge_session"]

                when (val outcome = webAuthnService.verifyLogin(
                    request.credentialId, request.clientDataJSON, request.authenticatorData, request.signature, cookie
                )) {
                    is LoginOutcome.CloneDetected -> {
                        val credentialId = outcome.credentialId
                        try {
                            userService.revokeCompromisedCredential(outcome.userId, credentialId)
                        } catch (e: Exception) {
                            call.application.log.error("Failed to notify compromised credentialId=${credentialId}", e)
                        }
                        throw UnauthorizedException("Sign count did not increase, possible cloned authenticator")
                    }
                    is LoginOutcome.Success -> {
                        val sid = Uuid.random().toString()
                        val accessToken = jwtService.issue(outcome.userId, sid)
                        val refresh = refreshTokenService.issue(outcome.userId, outcome.credentialId)
                        call.clearChallengeSessionCookie()
                        call.setAuthCookies(accessToken, refresh.refreshToken)
                        call.respond(mapOf("csrfToken" to csrfService.tokenFor(sid)))
                    }
                }
            }
        }

        rateLimit(authRefreshRateLimit) {
            post("/refresh") {
                val refreshToken = call.request.cookies["refresh_token"]
                val (userId, next) = refreshTokenService.rotate(refreshToken)
                val sid = Uuid.random().toString()
                val accessToken = jwtService.issue(userId, sid)

                call.setAuthCookies(accessToken, next.refreshToken)
                call.respond(mapOf("csrfToken" to csrfService.tokenFor(sid)))
            }

            post("/logout") {
                call.request.cookies["refresh_token"]?.let { refreshTokenService.revoke(it) }
                call.clearAuthCookies()
                call.respond(HttpStatusCode.NoContent)
            }
        }

        rateLimit(authChallengeRateLimit) {
            authenticatedPost("/credentials/add/start") { startChallenge(call) }
            authenticatedPost("/credentials/add/finish") {
                val request = call.receive<AddCredentialFinishRequest>()
                val cookie = call.request.cookies["challenge_session"]
                val attestationObject = request.attestationObject
                val clientDataJSON = request.clientDataJSON

                val parsed = webAuthnService.verifyRegistration(clientDataJSON, attestationObject, cookie)
                userService.addCredential(parsed.credentialId, parsed.algorithm, parsed.publicKey)

                call.clearChallengeSessionCookie()
                call.respond(HttpStatusCode.Created)
            }
        }

        authenticatedGet("/credentials") {
            call.respond(userService.listCredentials())
        }

        authenticatedDelete("/credentials/{id}") {
            val credentialId = Uuid.parse(call.parameters.getOrFail("id"))
            userService.deleteCredential(credentialId)
            call.respond(HttpStatusCode.NoContent)
        }

        authenticatedDelete("/account") {
            userService.deleteAccount()
            call.clearAuthCookies()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.setCookie(name: String, value: String, path: String, expires: GMTDate? = null) {
    response.cookies.append(
        Cookie(
            name = name,
            value = value,
            httpOnly = true,
            secure = true,
            extensions = mapOf("SameSite" to "Strict"),
            path = path,
            expires = expires
        )
    )
}

private fun ApplicationCall.setAuthCookies(accessToken: String, refreshToken: String) {
    setCookie("access_token", accessToken, "/")
    setCookie("refresh_token", refreshToken, "/auth/refresh")
}

private fun ApplicationCall.clearAuthCookies() {
    setCookie("access_token", "", "/", GMTDate(0))
    setCookie("refresh_token", "", "/auth/refresh", GMTDate(0))
}

private fun ApplicationCall.clearChallengeSessionCookie() {
    setCookie("challenge_session", "", "/auth", GMTDate(0))
}
