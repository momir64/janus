package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.crypto.authentication.CsrfService
import rs.moma.janus.kredenac.crypto.authentication.JwtService
import rs.moma.janus.kredenac.plugins.configureAuthentication
import rs.moma.janus.kredenac.plugins.configureSerialization
import rs.moma.janus.kredenac.plugins.configureStatusPages
import rs.moma.janus.kredenac.plugins.authenticatedGet
import io.ktor.server.testing.testApplication
import rs.moma.janus.kredenac.utils.TestInfra
import io.ktor.server.response.respondText
import io.ktor.server.application.install
import io.ktor.server.routing.routing
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.client.request.get
import io.ktor.client.HttpClient
import org.koin.ktor.plugin.Koin
import io.ktor.http.HttpHeaders
import kotlin.test.assertEquals
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.uuid.Uuid

class SessionGateTest {
    private val jwt = JwtService(TestInfra.hmacSecret)
    private val credentialId = Uuid.random()
    private val userId = Uuid.random()

    private fun app(block: suspend (HttpClient) -> Unit) = testApplication {
        application {
            install(Koin) {
                modules(module {
                    single { jwt }
                    single { CsrfService(TestInfra.hmacSecret) }
                })
            }
            configureSerialization()
            configureStatusPages()
            configureAuthentication()
            routing {
                authenticatedGet("/anything") { call.respondText("anything") }
                authenticatedGet("/files", privezakOnly = true) { call.respondText("files") }
            }
        }
        block(createClient { })
    }

    private suspend fun HttpClient.visit(path: String, privezak: Boolean) =
        get(path) {
            header(HttpHeaders.Cookie, "access_token=${jwt.issue(userId, "sid", credentialId, privezak)}")
        }

    @Test
    fun `a privezak session reaches the files route`() = app { client ->
        assertEquals(HttpStatusCode.OK, client.visit("/files", privezak = true).status)
    }

    @Test
    fun `a session signed in with any other passkey is refused`() = app { client ->
        assertEquals(HttpStatusCode.Forbidden, client.visit("/files", privezak = false).status)
    }

    @Test
    fun `that session still reaches everything else`() = app { client ->
        assertEquals(HttpStatusCode.OK, client.visit("/anything", privezak = false).status)
    }

    @Test
    fun `no token at all is unauthorized rather than forbidden`() = app { client ->
        assertEquals(HttpStatusCode.Unauthorized, client.get("/files").status)
    }
}
