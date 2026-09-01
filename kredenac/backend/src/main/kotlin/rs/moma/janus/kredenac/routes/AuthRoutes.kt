package rs.moma.janus.kredenac.routes

import rs.moma.janus.kredenac.crypto.authentication.RefreshTokenService
import rs.moma.janus.kredenac.crypto.authentication.MagicLinkService
import rs.moma.janus.kredenac.crypto.webauthn.verifyRegistration
import rs.moma.janus.kredenac.crypto.authentication.CsrfService
import rs.moma.janus.kredenac.crypto.webauthn.ParsedAttestation
import rs.moma.janus.kredenac.crypto.authentication.JwtService
import rs.moma.janus.kredenac.crypto.webauthn.WebAuthnService
import rs.moma.janus.kredenac.plugins.authChallengeRateLimit
import rs.moma.janus.kredenac.common.UnauthorizedException
import rs.moma.janus.kredenac.crypto.webauthn.LoginOutcome
import rs.moma.janus.kredenac.plugins.authRefreshRateLimit
import rs.moma.janus.kredenac.crypto.webauthn.verifyLogin
import rs.moma.janus.kredenac.plugins.authenticatedDelete
import rs.moma.janus.kredenac.plugins.sessionCredentialId
import rs.moma.janus.kredenac.dtos.RegisterVerifyRequest
import rs.moma.janus.kredenac.plugins.magicLinkRateLimit
import rs.moma.janus.kredenac.plugins.authenticatedPost
import rs.moma.janus.kredenac.plugins.authenticatedGet
import rs.moma.janus.kredenac.dtos.AttestationRequest
import rs.moma.janus.kredenac.dtos.ChallengeResponse
import rs.moma.janus.kredenac.dtos.AssertionRequest
import rs.moma.janus.kredenac.dtos.SessionResponse
import rs.moma.janus.kredenac.services.UserService
import io.ktor.server.plugins.ratelimit.rateLimit
import rs.moma.janus.kredenac.common.ClientInfo
import rs.moma.janus.kredenac.common.clientInfo
import rs.moma.janus.kredenac.common.uuidParam
import rs.moma.janus.kredenac.plugins.API_ROOT
import rs.moma.janus.kredenac.common.ownerId
import rs.moma.janus.kredenac.dtos.TokenDto
import io.ktor.server.plugins.origin
import org.koin.core.qualifier.named
import io.ktor.server.application.*
import io.ktor.util.date.GMTDate
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import io.ktor.http.*

private const val AUTH_PATH = "$API_ROOT/auth"

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
        call.setCookie("challenge_session", session.cookie, AUTH_PATH)
        call.respond(mapOf("challenge" to session.challenge, "rpId" to rpId))
    }

    suspend fun verifyAttestation(call: ApplicationCall): Pair<ParsedAttestation, String> {
        val request = call.receive<AttestationRequest>()
        val cookie = call.request.cookies["challenge_session"]
        return webAuthnService.verifyRegistration(request.clientDataJSON, request.attestationObject, cookie)
    }

    suspend fun verifyAssertion(call: ApplicationCall): LoginOutcome {
        val request = call.receive<AssertionRequest>()
        val cookie = call.request.cookies["challenge_session"]
        val (ip, location) = call.clientOrigin()
        return webAuthnService.verifyLogin(
            request.credentialId, request.clientDataJSON, request.authenticatorData,
            request.signature, cookie, ip, location
        )
    }

    suspend fun revokeClone(call: ApplicationCall, outcome: LoginOutcome.CloneDetected): Nothing {
        try {
            userService.revokeCompromisedCredential(outcome.userId, outcome.credentialId)
        } catch (e: Exception) {
            call.application.log.error("Failed to notify compromised credentialId=${outcome.credentialId}", e)
        }
        throw UnauthorizedException("Sign count did not increase, possible cloned authenticator", "passkey_cloned")
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
            post("/register/start") {
                val token = call.receive<TokenDto>().token
                val session = webAuthnService.start(token)
                val email = magicLinkService.getEmail(token)
                val (userHandle, credentialIds) = userService.prepareRegistration(email)

                call.setCookie("challenge_session", session.cookie, AUTH_PATH)
                call.respond(ChallengeResponse(credentialIds, session.challenge, rpId, email, userHandle))
            }

            post("/register/finish") {
                val (parsed, email) = verifyAttestation(call)
                userService.register(email, parsed)
                call.clearChallengeSessionCookie()
                call.respond(HttpStatusCode.Created)
            }

            post("/login/start") { startChallenge(call) }
            post("/login/finish") {
                when (val outcome = verifyAssertion(call)) {
                    is LoginOutcome.CloneDetected -> revokeClone(call, outcome)
                    is LoginOutcome.Success -> {
                        val sid = Uuid.random().toString()
                        val csrf = csrfService.tokenFor(sid)
                        val accessToken = jwtService.issue(outcome.userId, sid, outcome.credentialId)
                        val refresh = refreshTokenService.issue(outcome.userId, outcome.credentialId)
                        call.clearChallengeSessionCookie()
                        call.setAuthCookies(accessToken, refresh.refreshToken)
                        call.respond(SessionResponse(csrf, jwtService.accessTokenTtl.inWholeSeconds))
                    }
                }
            }
        }

        rateLimit(authRefreshRateLimit) {
            post("/refresh") {
                val refreshToken = call.request.cookies["refresh_token"]
                val (userId, next) = refreshTokenService.rotate(refreshToken)
                val sid = Uuid.random().toString()
                val csrf = csrfService.tokenFor(sid)
                val accessToken = jwtService.issue(userId, sid, next.credentialId)

                call.setAuthCookies(accessToken, next.refreshToken)
                call.respond(SessionResponse(csrf, jwtService.accessTokenTtl.inWholeSeconds))
            }

            post("/logout") {
                call.request.cookies["refresh_token"]?.let { refreshTokenService.revoke(it) }
                call.clearAuthCookies()
                call.respond(HttpStatusCode.NoContent)
            }
        }

        rateLimit(authChallengeRateLimit) {
            authenticatedPost("/credentials/add/start") { startChallenge(call) }
            authenticatedPost("/credentials/add/verify") {
                val outcome = verifyAssertion(call)
                if (outcome is LoginOutcome.CloneDetected) revokeClone(call, outcome)
                if (outcome !is LoginOutcome.Success || outcome.userId != ownerId)
                    throw UnauthorizedException("Passkey does not belong to this account")

                val session = webAuthnService.start(userService.issueReauthToken())

                call.setCookie("challenge_session", session.cookie, AUTH_PATH)
                call.respond(
                    ChallengeResponse(
                        userService.credentialIds(), session.challenge, rpId,
                        userService.email(), userService.userHandle()
                    )
                )
            }

            authenticatedPost("/credentials/add/finish") {
                val (parsed, userId) = verifyAttestation(call)
                if (userId != ownerId.toString())
                    throw UnauthorizedException("Challenge does not belong to this session")

                userService.addCredential(parsed, call.clientInfo())

                call.clearChallengeSessionCookie()
                call.respond(HttpStatusCode.Created)
            }
        }

        authenticatedGet("/credentials") {
            call.respond(userService.listCredentials(sessionCredentialId))
        }

        authenticatedDelete("/credentials/{id}") {
            val credentialId = call.uuidParam("id")
            userService.deleteCredential(credentialId, call.clientInfo())
            call.respond(HttpStatusCode.NoContent)
        }

        rateLimit(authChallengeRateLimit) {
            authenticatedPost("/reauth/start") { startChallenge(call) }
            authenticatedPost("/reauth/finish") {
                val outcome = verifyAssertion(call)

                if (outcome is LoginOutcome.CloneDetected) revokeClone(call, outcome)
                if (outcome !is LoginOutcome.Success || outcome.userId != ownerId)
                    throw UnauthorizedException("Passkey does not belong to this account")

                call.clearChallengeSessionCookie()
                call.respond(TokenDto(userService.issueReauthToken()))
            }
        }

        authenticatedDelete("/account") {
            userService.consumeReauthToken(call.receive<TokenDto>().token)
            userService.deleteAccount(call.clientInfo())
            call.clearAuthCookies()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun ApplicationCall.clientOrigin(): Pair<String?, String?> {
    val city = request.headers["CF-IPCity"]
    val country = request.headers["CF-IPCountry"]
    val ip = request.headers["CF-Connecting-IP"] ?: request.origin.remoteHost
    return ip to listOfNotNull(city, country).joinToString(", ").ifBlank { null }
}

private fun ApplicationCall.clientInfo(): ClientInfo {
    val (ip, location) = clientOrigin()
    return clientInfo(request.headers["User-Agent"], ip, location)
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
    setCookie("refresh_token", refreshToken, AUTH_PATH)
}

private fun ApplicationCall.clearAuthCookies() {
    setCookie("access_token", "", "/", GMTDate(0))
    setCookie("refresh_token", "", AUTH_PATH, GMTDate(0))
}

private fun ApplicationCall.clearChallengeSessionCookie() {
    setCookie("challenge_session", "", AUTH_PATH, GMTDate(0))
}
