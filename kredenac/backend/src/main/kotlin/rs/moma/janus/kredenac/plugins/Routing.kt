package rs.moma.janus.kredenac.plugins

import io.ktor.server.http.content.singlePageApplication
import rs.moma.janus.kredenac.routes.filesRoutes
import rs.moma.janus.kredenac.routes.notesRoutes
import rs.moma.janus.kredenac.routes.authRoutes
import rs.moma.janus.kredenac.common.Env
import io.ktor.server.application.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.*
import java.io.File

const val API_ROOT = "/api"

fun Application.configureRouting() {
    val frontend = Env.getOrNull("FRONTEND_DIST_PATH")?.let(::File)?.takeIf { it.isDirectory }

    routing {
        route(API_ROOT) {
            notesRoutes()
            filesRoutes()
            authRoutes()

            get("{...}") { call.error(HttpStatusCode.NotFound) }
        }

        frontend?.let { serveFrontend(it) }
    }
}

internal fun Route.serveFrontend(dist: File) {
    singlePageApplication {
        filesPath = dist.path
        defaultPage = "index.html"
        useResources = false
    }
}
