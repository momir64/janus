package rs.moma.janus.kredenac.plugins

import rs.moma.janus.kredenac.routes.filesRoutes
import rs.moma.janus.kredenac.routes.notesRoutes
import rs.moma.janus.kredenac.routes.authRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

const val API_ROOT = "/api"

fun Application.configureRouting() {
    routing {
        route(API_ROOT) {
            notesRoutes()
            filesRoutes()
            authRoutes()
        }
    }
}
