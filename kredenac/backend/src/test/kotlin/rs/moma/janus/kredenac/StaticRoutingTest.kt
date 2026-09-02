package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.plugins.serveFrontend
import io.ktor.server.testing.testApplication
import io.ktor.client.statement.bodyAsText
import io.ktor.server.response.respondText
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.routing.route
import io.ktor.http.HttpStatusCode
import io.ktor.client.request.get
import io.ktor.server.routing.get
import kotlin.test.assertEquals
import kotlin.test.Test
import java.io.File

class StaticRoutingTest {
    private fun dist(): File {
        val dir = File.createTempFile("dist", "").let { it.delete(); it.mkdirs(); it }
        File(dir, "index.html").writeText("SHELL")
        File(dir, "assets").mkdirs()
        File(dir, "assets/app.js").writeText("BUNDLE")
        return dir
    }

    @Test
    fun testBuiltFilesAreServedAndClientRoutesFallBackToTheShell() = testApplication {
        val dir = dist()
        application {
            routing {
                route("/api") {
                    get("/notes") { call.respondText("NOTES") }
                    get("{...}") { call.respond(HttpStatusCode.NotFound) }
                }
                serveFrontend(dir)
            }
        }

        assertEquals("BUNDLE", client.get("/assets/app.js").bodyAsText(), "a built asset was shadowed")
        assertEquals("SHELL", client.get("/").bodyAsText(), "the root did not serve the shell")
        assertEquals("SHELL", client.get("/settings").bodyAsText(), "a client route did not fall back")
        assertEquals("SHELL", client.get("/verify/abc123").bodyAsText(), "a nested client route did not fall back")
        assertEquals("NOTES", client.get("/api/notes").bodyAsText(), "an api route was shadowed")
        assertEquals(HttpStatusCode.NotFound, client.get("/api/unknown").status, "an unknown api path fell back")
    }
}
