package rs.moma.janus.kredenac.services

import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory.getLogger
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import io.ktor.client.request.setBody
import kotlinx.coroutines.Dispatchers
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import io.ktor.http.isSuccess

@Serializable
private data class EmailRequest(val from: String, val to: List<String>, val subject: String, val html: String)

class EmailService(private val apiKey: String, private val fromAddress: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val log = getLogger(EmailService::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO)

    suspend fun send(to: String, subject: String, html: String) {
        val body = json.encodeToString(EmailRequest(fromAddress, listOf(to), subject, html))
        val response = client.post("https://api.resend.com/emails") {
            header("Authorization", "Bearer $apiKey")
            header("Content-Type", "application/json")
            setBody(body)
        }

        if (!response.status.isSuccess()) {
            log.error("Resend API returned ${response.status.value}: ${response.bodyAsText()}")
            throw IllegalStateException("Failed to send the email")
        }
    }
    
    fun notify(to: String, subject: String, html: String) {
        scope.launch {
            try {
                send(to, subject, html)
            } catch (e: Exception) {
                log.error("Failed to send notification: $subject", e)
            }
        }
    }
}