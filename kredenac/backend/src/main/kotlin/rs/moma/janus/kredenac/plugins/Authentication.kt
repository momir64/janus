package rs.moma.janus.kredenac.plugins

import rs.moma.janus.kredenac.crypto.authentication.CsrfService
import rs.moma.janus.kredenac.crypto.authentication.JwtService
import io.ktor.server.auth.AuthenticationFailedCause.Error
import rs.moma.janus.kredenac.common.ForbiddenException
import rs.moma.janus.kredenac.common.Owner
import io.ktor.server.request.httpMethod
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.routing.Route
import io.ktor.server.application.*
import org.koin.ktor.ext.inject
import io.ktor.server.routing.*
import io.ktor.http.HttpMethod
import io.ktor.server.auth.*
import kotlin.uuid.Uuid
import io.ktor.http.*

data class SessionPrincipal(val userId: Uuid, val credentialId: Uuid, val privezak: Boolean)

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
                        call.error(HttpStatusCode.Unauthorized)
                        challenge.complete()
                    }
                    return@authenticate
                }

                if (context.call.request.httpMethod !in safeMethods) {
                    val csrfHeader = context.call.request.headers["X-CSRF-Token"]
                    if (!csrfService.isValid(claims.sid, csrfHeader)) {
                        context.challenge(this, Error("Invalid CSRF token")) { challenge, call ->
                            call.error(HttpStatusCode.Forbidden)
                            challenge.complete()
                        }
                        return@authenticate
                    }
                }

                context.principal(SessionPrincipal(Uuid.parse(claims.sub), Uuid.parse(claims.cid), claims.pzk))
            }
        }
    }
}

val RoutingContext.sessionCredentialId: Uuid
    get() = call.principal<SessionPrincipal>()!!.credentialId

fun Route.authenticated(
    method: HttpMethod, path: String, privezakOnly: Boolean = false,
    body: suspend context(Owner) RoutingContext.() -> Unit
) {
    authenticate("jwt-cookie") {
        route(path, method) {
            handle {
                val session = call.principal<SessionPrincipal>()!!
                if (privezakOnly && !session.privezak)
                    throw ForbiddenException("This session did not sign in with a Privezak passkey")
                context(Owner(session.userId, session.privezak)) { body() }
            }
        }
    }
}

fun Route.authenticatedGet(
    path: String, privezakOnly: Boolean = false, body: suspend context(Owner) RoutingContext.() -> Unit
) = authenticated(HttpMethod.Get, path, privezakOnly, body)

fun Route.authenticatedPost(
    path: String, privezakOnly: Boolean = false, body: suspend context(Owner) RoutingContext.() -> Unit
) = authenticated(HttpMethod.Post, path, privezakOnly, body)

fun Route.authenticatedPut(
    path: String, privezakOnly: Boolean = false, body: suspend context(Owner) RoutingContext.() -> Unit
) = authenticated(HttpMethod.Put, path, privezakOnly, body)

fun Route.authenticatedDelete(
    path: String, privezakOnly: Boolean = false, body: suspend context(Owner) RoutingContext.() -> Unit
) = authenticated(HttpMethod.Delete, path, privezakOnly, body)