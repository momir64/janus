package rs.moma.janus.kredenac.plugins

import io.ktor.server.auth.AuthenticationFailedCause.Error
import rs.moma.janus.kredenac.service.CsrfService
import rs.moma.janus.kredenac.service.JwtService
import io.ktor.server.request.httpMethod
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import io.ktor.server.auth.*
import io.ktor.http.*

data class SessionPrincipal(val userId: String, val sid: String)

private val safeMethods = setOf(HttpMethod.Get, HttpMethod.Head, HttpMethod.Options)

fun Application.configureAuthentication() {
    val jwtService: JwtService by inject()
    val csrfService: CsrfService by inject()

    install(Authentication) {
        provider("jwt-cookie") {
            authenticate { context ->
                val claims = context.call.request.cookies["access_token"]?.let(jwtService::verify)

                if (claims == null) {
                    context.challenge(this, Error("Invalid or missing access token")) { challenge, call ->
                        call.respond(HttpStatusCode.Unauthorized)
                        challenge.complete()
                    }
                    return@authenticate
                }

                if (context.call.request.httpMethod !in safeMethods) {
                    val csrfHeader = context.call.request.headers["X-CSRF-Token"]
                    if (!csrfService.isValid(claims.sid, csrfHeader)) {
                        context.challenge(this, Error("Invalid CSRF token")) { challenge, call ->
                            call.respond(HttpStatusCode.Forbidden)
                            challenge.complete()
                        }
                        return@authenticate
                    }
                }

                context.principal(SessionPrincipal(claims.sub, claims.sid))
            }
        }
    }
}