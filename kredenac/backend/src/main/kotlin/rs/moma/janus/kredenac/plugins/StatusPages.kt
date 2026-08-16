package rs.moma.janus.kredenac.plugins

import io.ktor.server.plugins.MissingRequestParameterException
import rs.moma.janus.kredenac.common.UnauthorizedException
import rs.moma.janus.kredenac.common.BadRequestException
import rs.moma.janus.kredenac.common.ForbiddenException
import rs.moma.janus.kredenac.common.ConflictException
import rs.moma.janus.kredenac.common.NotFoundException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.http.*

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<MissingRequestParameterException> { call, cause ->
            call.respondText(text = cause.message ?: "Missing parameter", status = HttpStatusCode.BadRequest)
        }
        exception<BadRequestException> { call, cause ->
            call.respondText(text = cause.message ?: "Bad request", status = HttpStatusCode.BadRequest)
        }
        exception<UnauthorizedException> { call, cause ->
            call.respondText(text = cause.message ?: "Unauthorized", status = HttpStatusCode.Unauthorized)
        }
        exception<ForbiddenException> { call, cause ->
            call.respondText(text = cause.message ?: "Forbidden", status = HttpStatusCode.Forbidden)
        }
        exception<NotFoundException> { call, cause ->
            call.respondText(text = cause.message ?: "Not found", status = HttpStatusCode.NotFound)
        }
        exception<ConflictException> { call, cause ->
            call.respondText(text = cause.message ?: "Conflict", status = HttpStatusCode.Conflict)
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.respondText(text = "Internal server error", status = HttpStatusCode.InternalServerError)
        }
        status(HttpStatusCode.TooManyRequests) { call, status ->
            call.respondText(text = "Too many requests", status = status)
        }
    }
}
