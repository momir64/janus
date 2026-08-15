package rs.moma.janus.kredenac.plugins

import rs.moma.janus.kredenac.routes.notesRoutes
import rs.moma.janus.kredenac.routes.authRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    routing {
        route("/api") {
            notesRoutes()
            authRoutes()
        }
    }
}