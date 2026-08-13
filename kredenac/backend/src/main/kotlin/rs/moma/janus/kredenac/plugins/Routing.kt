package rs.moma.janus.kredenac.plugins

import rs.moma.janus.kredenac.routes.notesRoutes
import rs.moma.janus.kredenac.routes.authRoutes
import org.koin.core.qualifier.named
import io.ktor.server.application.*
import org.koin.ktor.ext.inject
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val rpId: String by inject(named("rpId"))

    routing {
        route("/api") {
            notesRoutes()
            authRoutes(rpId)
        }
    }
}