package rs.moma.janus.kredenac.plugins

import io.ktor.server.routing.IgnoreTrailingSlash
import io.ktor.server.request.receiveText
import rs.moma.janus.kredenac.common.Env
import io.ktor.server.application.*
import io.ktor.http.HttpStatusCode
import kotlin.io.path.isDirectory
import rs.moma.janus.lokot.Lokot
import io.ktor.server.response.*
import io.ktor.http.ContentType
import io.ktor.server.routing.*
import kotlin.io.path.Path

const val LOKOT_PATH = "/lokot"

fun Application.configureUnlock(lokot: Lokot, rpId: String) {
    install(IgnoreTrailingSlash)

    val frontend = Env.getOrNull("FRONTEND_DIST_PATH")?.let(::Path)?.takeIf { it.isDirectory() }

    routing {
        route(LOKOT_PATH) {
            get { call.respondText(Lokot.page(LOKOT_PATH), ContentType.Text.Html) }
            get("challenge") { call.respondText(lokot.challenge(rpId).json, ContentType.Application.Json) }
            post("unlock") {
                if (lokot.unlock(call.receiveText())) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.Unauthorized)
            }
        }
        route("$API_ROOT/{...}") {
            handle { call.respond(HttpStatusCode.ServiceUnavailable) }
        }
        frontend?.let { serveFrontend(it) }
    }
}
