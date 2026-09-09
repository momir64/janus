package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.plugins.configureSerialization
import rs.moma.janus.kredenac.common.UnauthorizedException
import rs.moma.janus.kredenac.plugins.configureStatusPages
import rs.moma.janus.kredenac.common.CompromisedException
import rs.moma.janus.kredenac.common.BadRequestException
import rs.moma.janus.kredenac.common.ForbiddenException
import rs.moma.janus.kredenac.common.ConflictException
import rs.moma.janus.kredenac.common.NotFoundException
import io.ktor.server.testing.testApplication
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import io.ktor.client.request.setBody
import io.ktor.server.request.receive
import io.ktor.server.routing.routing
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.post
import io.ktor.client.request.get
import io.ktor.server.routing.get
import io.ktor.http.HttpHeaders
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

@Serializable
private data class Body(val value: String)

// The frontend branches on `code` and shows its own copy, so what has to hold here is
// the status, the shape, and that a compromised row is indistinguishable from any 500.
class StatusPagesTest {
    private fun app(block: suspend (io.ktor.client.HttpClient) -> Unit) = testApplication {
        application {
            configureSerialization()
            configureStatusPages()
            routing {
                get("/bad") { throw BadRequestException("Filename is too long") }
                get("/bad-empty") { throw BadRequestException() }
                get("/unauthorized") { throw UnauthorizedException("Missing refresh token") }
                get("/coded") { throw UnauthorizedException("Sign count did not increase", "passkey_cloned") }
                get("/forbidden") { throw ForbiddenException("Nope") }
                get("/missing") { throw NotFoundException("File not found") }
                get("/conflict") { throw ConflictException("Already there") }
                get("/compromised") { throw CompromisedException("row failed integrity check") }
                get("/boom") { throw IllegalStateException("secret internal detail") }
                post("/body") { call.receive<Body>() }
            }
        }
        block(client)
    }

    @Test
    fun `each failure answers with its status and a json message`() = app { client ->
        val cases = listOf(
            "/bad" to (HttpStatusCode.BadRequest to "Filename is too long"),
            "/unauthorized" to (HttpStatusCode.Unauthorized to "Missing refresh token"),
            "/forbidden" to (HttpStatusCode.Forbidden to "Nope"),
            "/missing" to (HttpStatusCode.NotFound to "File not found"),
            "/conflict" to (HttpStatusCode.Conflict to "Already there")
        )

        for ((path, expected) in cases) {
            val response = client.get(path)
            assertEquals(expected.first, response.status, path)
            assertEquals("""{"message":"${expected.second}"}""", response.bodyAsText(), path)
        }
    }

    @Test
    fun `an empty message falls back to the status description`() = app { client ->
        assertEquals("""{"message":"Bad Request"}""", client.get("/bad-empty").bodyAsText())
    }

    @Test
    fun `only a specific failure carries a code`() = app { client ->
        assertEquals(
            """{"message":"Sign count did not increase","code":"passkey_cloned"}""",
            client.get("/coded").bodyAsText()
        )
        assertTrue("code" !in client.get("/unauthorized").bodyAsText(), "a generic failure named a code")
    }

    @Test
    fun `a compromised row is answered exactly like any other unhandled failure`() = app { client ->
        val compromised = client.get("/compromised")
        val unhandled = client.get("/boom")

        assertEquals(HttpStatusCode.InternalServerError, compromised.status)
        assertEquals(unhandled.status, compromised.status)
        assertEquals(unhandled.bodyAsText(), compromised.bodyAsText())
        assertEquals("""{"message":"Internal Server Error"}""", compromised.bodyAsText())
    }

    @Test
    fun `an internal message never reaches the client`() = app { client ->
        assertTrue("secret internal detail" !in client.get("/boom").bodyAsText())
        assertTrue("integrity" !in client.get("/compromised").bodyAsText())
    }

    @Test
    fun `a malformed body is a bad request that names no internal type`() = app { client ->
        for (body in listOf("", "{oops", """{"wrong":1}""")) {
            val response = client.post("/body") {
                header(HttpHeaders.ContentType, "application/json")
                setBody(body)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status, "body: $body")
            assertEquals("""{"message":"Bad Request"}""", response.bodyAsText(), "body: $body")
        }
    }
}
