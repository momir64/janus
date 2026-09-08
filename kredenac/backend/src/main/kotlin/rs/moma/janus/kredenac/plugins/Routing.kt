package rs.moma.janus.kredenac.plugins

import io.ktor.server.http.content.singlePageApplication
import rs.moma.janus.kredenac.routes.filesRoutes
import rs.moma.janus.kredenac.routes.notesRoutes
import rs.moma.janus.kredenac.routes.authRoutes
import rs.moma.janus.kredenac.common.Env
import io.ktor.server.application.*
import io.ktor.http.HttpStatusCode
import kotlin.io.path.isDirectory
import kotlin.io.path.pathString
import io.ktor.server.routing.*
import kotlin.io.path.Path
import java.nio.file.Path

const val API_ROOT = "/api"

fun Application.configureRouting() {
    val frontend = Env.getOrNull("FRONTEND_DIST_PATH")?.let(::Path)?.takeIf { it.isDirectory() }

    routing {
        host(Env.get("LOKOT_RP_ID")) {
            route("{...}") { handle { call.error(HttpStatusCode.NotFound) } }
        }

        route(API_ROOT) {
            notesRoutes()
            filesRoutes()
            authRoutes()

            get("{...}") { call.error(HttpStatusCode.NotFound) }
        }

        frontend?.let { serveFrontend(it) }
    }
}

internal fun Route.serveFrontend(dist: Path) {
    singlePageApplication {
        filesPath = dist.pathString
        defaultPage = "index.html"
        useResources = false
    }
}
