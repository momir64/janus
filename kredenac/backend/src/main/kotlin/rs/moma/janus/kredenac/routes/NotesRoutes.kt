package rs.moma.janus.kredenac.routes

import rs.moma.janus.kredenac.plugins.SessionPrincipal
import rs.moma.janus.kredenac.model.CreateNoteRequest
import rs.moma.janus.kredenac.model.UpdateNoteRequest
import rs.moma.janus.kredenac.service.NotesService
import io.ktor.server.util.getOrFail
import io.ktor.server.response.*
import org.koin.ktor.ext.inject
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import kotlin.uuid.Uuid
import io.ktor.http.*


fun Route.notesRoutes() {
    val notesService: NotesService by inject()

    route("/notes") {
        authenticate("jwt-cookie") {
            get {
                val principal = call.principal<SessionPrincipal>()!!
                val notes = notesService.list(Uuid.parse(principal.userId))
                call.respond(notes)
            }

            post {
                val principal = call.principal<SessionPrincipal>()!!
                val request = call.receive<CreateNoteRequest>()
                val note = notesService.create(Uuid.parse(principal.userId), request.title, request.content)
                call.respond(HttpStatusCode.Created, note)
            }

            put("/{id}") {
                val principal = call.principal<SessionPrincipal>()!!
                val noteId = Uuid.parse(call.parameters.getOrFail("id"))
                val request = call.receive<UpdateNoteRequest>()
                val note = notesService.update(Uuid.parse(principal.userId), noteId, request.title, request.content)
                call.respond(note)
            }

            delete("/{id}") {
                val principal = call.principal<SessionPrincipal>()!!
                val noteId = Uuid.parse(call.parameters.getOrFail("id"))
                notesService.delete(Uuid.parse(principal.userId), noteId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}