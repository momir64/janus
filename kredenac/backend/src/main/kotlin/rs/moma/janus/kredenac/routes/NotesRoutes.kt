package rs.moma.janus.kredenac.routes

import rs.moma.janus.kredenac.plugins.authenticatedDelete
import rs.moma.janus.kredenac.plugins.authenticatedPost
import rs.moma.janus.kredenac.plugins.authenticatedGet
import rs.moma.janus.kredenac.plugins.authenticatedPut
import rs.moma.janus.kredenac.dtos.CreateNoteRequest
import rs.moma.janus.kredenac.dtos.UpdateNoteRequest
import rs.moma.janus.kredenac.services.NotesService
import io.ktor.server.util.getOrFail
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import io.ktor.server.request.*
import io.ktor.server.routing.*
import kotlin.uuid.Uuid
import io.ktor.http.*

fun Route.notesRoutes() {
    val notesService: NotesService by inject()

    route("/notes") {
        authenticatedGet("") {
            call.respond(notesService.list())
        }

        authenticatedPost("") {
            val request = call.receive<CreateNoteRequest>()
            notesService.create(request.title, request.content)
            call.respond(HttpStatusCode.Created)
        }

        authenticatedPut("/{id}") {
            val noteId = Uuid.parse(call.parameters.getOrFail("id"))
            val request = call.receive<UpdateNoteRequest>()
            notesService.update(noteId, request.title, request.content)
            call.respond(HttpStatusCode.NoContent)
        }

        authenticatedDelete("/{id}") {
            val noteId = Uuid.parse(call.parameters.getOrFail("id"))
            notesService.delete(noteId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}