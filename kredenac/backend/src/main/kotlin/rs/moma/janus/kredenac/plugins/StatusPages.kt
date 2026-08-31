package rs.moma.janus.kredenac.plugins

import io.ktor.server.plugins.BadRequestException as KtorBadRequestException
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.server.plugins.MissingRequestParameterException
import io.ktor.http.HttpStatusCode.Companion.TooManyRequests
import io.ktor.server.plugins.ContentTransformationException
import rs.moma.janus.kredenac.common.UnauthorizedException
import io.ktor.http.HttpStatusCode.Companion.Unauthorized
import rs.moma.janus.kredenac.common.CompromisedException
import rs.moma.janus.kredenac.common.BadRequestException
import io.ktor.http.HttpStatusCode.Companion.BadRequest
import rs.moma.janus.kredenac.common.ForbiddenException
import io.ktor.http.HttpStatusCode.Companion.Forbidden
import rs.moma.janus.kredenac.common.ConflictException
import rs.moma.janus.kredenac.common.NotFoundException
import io.ktor.http.HttpStatusCode.Companion.Conflict
import io.ktor.http.HttpStatusCode.Companion.NotFound
import kotlinx.serialization.SerializationException
import rs.moma.janus.kredenac.common.ApiException
import rs.moma.janus.kredenac.dtos.ErrorResponse
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.http.*

internal suspend fun ApplicationCall.error(status: HttpStatusCode, message: String? = null, code: String? = null) =
    respond(status, ErrorResponse(message?.ifBlank { null } ?: status.description, code))

private suspend fun ApplicationCall.error(status: HttpStatusCode, cause: ApiException) =
    error(status, cause.message, cause.code)

fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<MissingRequestParameterException> { call, cause -> call.error(BadRequest, cause.message) }
        exception<UnauthorizedException> { call, cause -> call.error(Unauthorized, cause) }
        exception<BadRequestException> { call, cause -> call.error(BadRequest, cause) }
        exception<ForbiddenException> { call, cause -> call.error(Forbidden, cause) }
        exception<NotFoundException> { call, cause -> call.error(NotFound, cause) }
        exception<ConflictException> { call, cause -> call.error(Conflict, cause) }

        exception<ContentTransformationException> { call, _ -> call.error(BadRequest) }
        exception<KtorBadRequestException> { call, _ -> call.error(BadRequest) }
        exception<SerializationException> { call, _ -> call.error(BadRequest) }

        status(TooManyRequests) { call, status -> call.error(status) }

        exception<CompromisedException> { call, cause ->
            call.application.log.error("Compromised data", cause)
            call.error(InternalServerError)
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Unhandled exception", cause)
            call.error(InternalServerError)
        }
    }
}
