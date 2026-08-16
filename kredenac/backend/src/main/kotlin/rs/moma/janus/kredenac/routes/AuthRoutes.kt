package rs.moma.janus.kredenac.routes

import rs.moma.janus.kredenac.crypto.authentication.RefreshTokenService
import rs.moma.janus.kredenac.crypto.authentication.CsrfService
import rs.moma.janus.kredenac.crypto.authentication.JwtService
import rs.moma.janus.kredenac.crypto.webauthn.verifyRegistration
import rs.moma.janus.kredenac.model.AddCredentialFinishRequest
import rs.moma.janus.kredenac.crypto.webauthn.WebAuthnService
import rs.moma.janus.kredenac.model.RegisterFinishRequest
import rs.moma.janus.kredenac.utils.UnauthorizedException
import rs.moma.janus.kredenac.model.LoginFinishRequest
import rs.moma.janus.kredenac.plugins.SessionPrincipal
import rs.moma.janus.kredenac.crypto.webauthn.verifyLogin
import rs.moma.janus.kredenac.service.UserService
import org.koin.core.qualifier.named
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
    val refreshTokenService: RefreshTokenService by inject()
    val rpId: String by inject(named("rpId"))
    val webAuthnService: WebAuthnService by inject()
    val userService: UserService by inject()
    val csrfService: CsrfService by inject()
    val jwtService: JwtService by inject()

    suspend fun startChallenge(call: ApplicationCall) {
        val session = webAuthnService.start()
        call.setCookie("challenge_session", session.cookie, "/auth")
        call.respond(mapOf("challenge" to session.challenge, "rpId" to rpId))
    }

    route("/auth") {
        post("/register/start") { startChallenge(call) }
        post("/register/finish") {
            val request = call.receive<RegisterFinishRequest>()
            val cookie = call.request.cookies["challenge_session"]

            val parsed = webAuthnService.verifyRegistration(request.clientDataJSON, request.attestationObject, cookie)
            userService.createUser(request.email, parsed.credentialId, parsed.algorithm, parsed.publicKey)

            call.clearChallengeSessionCookie()
            call.respond(HttpStatusCode.Created)
        }

        post("/login/start") { startChallenge(call) }
        post("/login/finish") {
            val request = call.receive<LoginFinishRequest>()
            val cookie = call.request.cookies["challenge_session"]

            val userId = webAuthnService.verifyLogin(
                request.credentialId, request.clientDataJSON,
                request.authenticatorData, request.signature, cookie
            )

            val sid = Uuid.random().toString()
            val accessToken = jwtService.issue(userId, sid)
            val refresh = refreshTokenService.issue(userId)

            call.clearChallengeSessionCookie()
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
            post("/credentials/add/start") { startChallenge(call) }
            post("/credentials/add/finish") {
                val principal = call.principal<SessionPrincipal>()!!
                val request = call.receive<AddCredentialFinishRequest>()
                val cookie = call.request.cookies["challenge_session"]

                val parsed = webAuthnService.verifyRegistration(request.clientDataJSON, request.attestationObject, cookie)
                userService.addCredential(Uuid.parse(principal.userId), parsed.credentialId, parsed.algorithm, parsed.publicKey)

                call.clearChallengeSessionCookie()
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

private fun ApplicationCall.clearChallengeSessionCookie() {
    setCookie("challenge_session", "", "/auth", GMTDate(0))
}
