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

fun Application.configureUnlock(lokot: Lokot) {
    install(IgnoreTrailingSlash)

    val host = Env.get("LOKOT_RP_ID")
    val home = "https://${Env.get("RP_ID")}/"
    val frontend = Env.getOrNull("FRONTEND_DIST_PATH")?.let(::Path)?.takeIf { it.isDirectory() }

    routing {
        host(host) {
            get { call.respondText(Lokot.page(), ContentType.Text.Html) }
            get("challenge") { call.respondText(lokot.challenge(host, home).json, ContentType.Application.Json) }
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
