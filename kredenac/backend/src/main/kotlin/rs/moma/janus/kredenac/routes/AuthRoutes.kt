package rs.moma.janus.kredenac.routes

import rs.moma.janus.kredenac.model.RegisterFinishRequest
import rs.moma.janus.kredenac.service.RefreshTokenService
import rs.moma.janus.kredenac.service.RegistrationService
import rs.moma.janus.kredenac.utils.UnauthorizedException
import rs.moma.janus.kredenac.model.RegisterStartRequest
import rs.moma.janus.kredenac.model.LoginFinishRequest
import rs.moma.janus.kredenac.service.AssertionService
import rs.moma.janus.kredenac.model.LoginStartRequest
import rs.moma.janus.kredenac.service.CsrfService
import rs.moma.janus.kredenac.service.UserService
import rs.moma.janus.kredenac.service.JwtService
import io.ktor.server.application.*
import io.ktor.util.date.GMTDate
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import io.ktor.http.*

fun Route.authRoutes(rpId: String) {
    val registrationService: RegistrationService by inject()
    val refreshTokenService: RefreshTokenService by inject()
    val assertionService: AssertionService by inject()
    val userService: UserService by inject()
    val csrfService: CsrfService by inject()
    val jwtService: JwtService by inject()

    route("/auth") {
        post("/register/start") {
            val request = call.receive<RegisterStartRequest>()
            val pending = registrationService.startRegistration(request.email)
            call.respond(mapOf("challenge" to pending.challenge, "rpId" to rpId))
        }

        post("/register/finish") {
            val request = call.receive<RegisterFinishRequest>()
            val parsed = registrationService.verifyAndExtract(request.email, request.clientDataJSON, request.attestationObject)
            userService.createUser( parsed.email, parsed.credentialId, parsed.publicKeyX, parsed.publicKeyY)
            call.respond(HttpStatusCode.Created)
        }

        post("/login/start") {
            val request = call.receive<LoginStartRequest>()
            val pending = assertionService.startAssertion(request.email)
            call.respond(mapOf("challenge" to pending.challenge, "rpId" to rpId))
        }

        post("/login/finish") {
            val request = call.receive<LoginFinishRequest>()

            val result = assertionService.verify(
                request.email, request.credentialId, request.clientDataJSON,
                request.authenticatorData, request.signature
            )

            val sid = Uuid.random().toString()
            val accessToken = jwtService.issue(result.userId, sid)
            val refresh = refreshTokenService.issue(result.userId)

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