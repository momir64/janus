package rs.moma.janus.kredenac.routes

import rs.moma.janus.kredenac.crypto.authentication.RefreshTokenService
import rs.moma.janus.kredenac.crypto.webauthn.RegistrationService
import rs.moma.janus.kredenac.crypto.authentication.CsrfService
import rs.moma.janus.kredenac.crypto.authentication.JwtService
import rs.moma.janus.kredenac.crypto.webauthn.AssertionService
import rs.moma.janus.kredenac.crypto.webauthn.WebAuthnCeremony
import rs.moma.janus.kredenac.model.AddCredentialFinishRequest
import rs.moma.janus.kredenac.crypto.webauthn.ChallengeKind.*
import rs.moma.janus.kredenac.crypto.webauthn.ChallengeKind
import rs.moma.janus.kredenac.model.RegisterFinishRequest
import rs.moma.janus.kredenac.utils.UnauthorizedException
import rs.moma.janus.kredenac.utils.BadRequestException
import rs.moma.janus.kredenac.model.LoginFinishRequest
import rs.moma.janus.kredenac.plugins.SessionPrincipal
import rs.moma.janus.kredenac.model.LoginStartRequest
import rs.moma.janus.kredenac.service.UserService
import io.ktor.server.application.*
import io.ktor.util.date.GMTDate
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import kotlin.uuid.Uuid
import io.ktor.http.*

fun Route.authRoutes() {
    val rpId: String by inject(org.koin.core.qualifier.named("rpId"))
    val registrationService: RegistrationService by inject()
    val refreshTokenService: RefreshTokenService by inject()
    val assertionService: AssertionService by inject()
    val ceremony: WebAuthnCeremony by inject()
    val userService: UserService by inject()
    val csrfService: CsrfService by inject()
    val jwtService: JwtService by inject()

    route("/auth") {
        post("/register/start") {
            val challenge = registrationService.startRegistration()
            call.setChallengeBindingCookie(ceremony, REGISTRATION, challenge)
            call.respond(mapOf("challenge" to challenge, "rpId" to rpId))
        }

        post("/register/finish") {
            val request = call.receive<RegisterFinishRequest>()
            val cookie = call.request.cookies["challenge_binding"] ?: throw BadRequestException("No challenge binding cookie")

            val parsed = registrationService.verifyAndExtract(request.email, request.clientDataJSON, request.attestationObject, cookie)
            userService.createUser(parsed.email, parsed.credentialId, parsed.algorithm, parsed.publicKey)

            call.clearChallengeBindingCookie()
            call.respond(HttpStatusCode.Created)
        }

        post("/login/start") {
            val request = call.receive<LoginStartRequest>()
            val pending = assertionService.startAssertion(request.email)
            call.setChallengeBindingCookie(ceremony, ASSERTION, pending.challenge)
            call.respond(mapOf("challenge" to pending.challenge, "rpId" to rpId, "allowCredentials" to pending.allowedCredentialIds))
        }

        post("/login/finish") {
            val request = call.receive<LoginFinishRequest>()
            val cookie = call.request.cookies["challenge_binding"]

            val result = assertionService.verify(
                request.credentialId, request.clientDataJSON,
                request.authenticatorData, request.signature, cookie
            )

            val sid = Uuid.random().toString()
            val accessToken = jwtService.issue(result.userId, sid)
            val refresh = refreshTokenService.issue(result.userId)

            call.clearChallengeBindingCookie()
            call.setAuthCookies(accessToken, refresh.refreshToken)
            call.respond(mapOf("csrfToken" to csrfService.tokenFor(sid)))
        }

        post("/refresh") {
            val refreshToken = call.request.cookies["refresh_token"]
                ?: throw UnauthorizedException("Missing refresh token")

            val (userId, next) = refreshTokenService.rotate(refreshToken)
            val sid = Uuid.random().toString()
            val accessToken = jwtService.issue(userId, sid)

            call.setAuthCookies(accessToken, next.refreshToken)
            call.respond(mapOf("csrfToken" to csrfService.tokenFor(sid)))
        }

        post("/logout") {
            call.clearAuthCookies()
            call.respond(HttpStatusCode.NoContent)
        }

        authenticate("jwt-cookie") {
            post("/credentials/add/start") {
                val challenge = registrationService.startRegistration()
                call.setChallengeBindingCookie(ceremony, REGISTRATION, challenge)
                call.respond(mapOf("challenge" to challenge, "rpId" to rpId))
            }

            post("/credentials/add/finish") {
                val principal = call.principal<SessionPrincipal>()!!
                val user = userService.getById(Uuid.parse(principal.userId))
                val request = call.receive<AddCredentialFinishRequest>()
                val cookie = call.request.cookies["challenge_binding"] ?: throw BadRequestException("No challenge binding cookie")

                val parsed = registrationService.verifyAndExtract(user.email, request.clientDataJSON, request.attestationObject, cookie)
                userService.addCredential(user.id, parsed.credentialId, parsed.algorithm, parsed.publicKey)

                call.clearChallengeBindingCookie()
                call.respond(HttpStatusCode.Created)
            }
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

private fun ApplicationCall.setChallengeBindingCookie(ceremony: WebAuthnCeremony, kind: ChallengeKind, challenge: String) {
    setCookie("challenge_binding", ceremony.challengeHash(challenge, kind), "/auth")
}

private fun ApplicationCall.clearChallengeBindingCookie() {
    setCookie("challenge_binding", "", "/auth", GMTDate(0))
}
