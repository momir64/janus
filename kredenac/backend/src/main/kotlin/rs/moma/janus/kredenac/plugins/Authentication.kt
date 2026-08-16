package rs.moma.janus.kredenac.plugins

import rs.moma.janus.kredenac.crypto.authentication.CsrfService
import rs.moma.janus.kredenac.crypto.authentication.JwtService
import io.ktor.server.auth.AuthenticationFailedCause.Error
import rs.moma.janus.kredenac.common.Owner
import io.ktor.server.request.httpMethod
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.application.*
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import io.ktor.server.routing.*
import io.ktor.http.HttpMethod
import io.ktor.server.auth.*
import kotlin.uuid.Uuid
import io.ktor.http.*

data class SessionPrincipal(val userId: Uuid)

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

                context.principal(SessionPrincipal(Uuid.parse(claims.sub)))
            }
        }
    }
}

fun Route.authenticated(method: HttpMethod, path: String, body: suspend context(Owner) RoutingContext.() -> Unit) {
    authenticate("jwt-cookie") {
        route(path, method) {
            handle {
                val owner = Owner(call.principal<SessionPrincipal>()!!.userId)
                context(owner) { body() }
            }
        }
    }
}

fun Route.authenticatedGet(path: String, body: suspend context(Owner) RoutingContext.() -> Unit) =
    authenticated(HttpMethod.Get, path, body)

fun Route.authenticatedPost(path: String, body: suspend context(Owner) RoutingContext.() -> Unit) =
    authenticated(HttpMethod.Post, path, body)

fun Route.authenticatedPut(path: String, body: suspend context(Owner) RoutingContext.() -> Unit) =
    authenticated(HttpMethod.Put, path, body)

fun Route.authenticatedDelete(path: String, body: suspend context(Owner) RoutingContext.() -> Unit) =
    authenticated(HttpMethod.Delete, path, body)