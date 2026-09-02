package rs.moma.janus.kredenac.services

import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import rs.moma.janus.kredenac.common.ClientInfo
import java.util.concurrent.ConcurrentHashMap
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory.getLogger
import kotlinx.coroutines.SupervisorJob
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import io.ktor.client.request.setBody
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.LocalDateTime
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.HttpClient
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format
import io.ktor.http.isSuccess
import kotlin.time.Clock

@Serializable
private data class EmailRequest(val from: String, val to: List<String>, val subject: String, val html: String)

class EmailService(
    private val apiKey: String,
    private val fromAddress: String,
    private val homeUrl: String
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val templates = ConcurrentHashMap<String, String>()
    private val log = getLogger(EmailService::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(CIO)
    private val timeFormat = LocalDateTime.Format {
        @OptIn(FormatStringsInDatetimeFormats::class)
        byUnicodePattern("HH:mm d.M.yyyy.")
    }

    suspend fun sendRegisterMagicLink(to: String, link: String) {
        val subject = "Finish signing up for Kredenac"
        val intro = "Click the button below to finish creating your account."
        send(to, subject, render("magic-link", subject, mapOf("intro" to intro, "link" to link)))
    }

    suspend fun sendRecoveryMagicLink(to: String, link: String) {
        val subject = "Add a new passkey to your Kredenac account"
        val intro = "Click the button below to register a new passkey for your account."
        send(to, subject, render("magic-link", subject, mapOf("intro" to intro, "link" to link)))
    }

    fun notifyPasskeyAdded(to: String, client: ClientInfo) {
        val subject = "New passkey was added to your Kredenac account"
        notify(to, subject, render("passkey-change", subject, mapOf("action" to "added to"), rows(client)))
    }

    fun notifyPasskeyRemoved(to: String, client: ClientInfo) {
        val subject = "Passkey was removed from your Kredenac account"
        notify(to, subject, render("passkey-change", subject, mapOf("action" to "removed from"), rows(client)))
    }

    fun notifyPasskeyDisabled(to: String, client: ClientInfo) {
        val subject = "Security alert: one of your passkeys was disabled"
        notify(to, subject, render("passkey-disabled", subject, rows = rows(client)))
    }

    fun notifyAccountDeleted(to: String) {
        val subject = "Your Kredenac account was deleted"
        notify(to, subject, render("account-deleted", subject))
    }

    private fun rows(client: ClientInfo) = listOfNotNull(
        client.device?.let { "Device" to it },
        client.browser?.let { "Browser" to it },
        client.ip?.let { "IP address" to it },
        client.location?.let { "Location" to it },
        "Time" to time(client.timezone)
    ).filter { it.second.isNotBlank() }

    private fun time(timezone: String?): String {
        val zone = timezone?.let { runCatching { TimeZone.of(it) }.getOrNull() }
        val formatted = Clock.System.now().toLocalDateTime(zone ?: TimeZone.UTC).format(timeFormat)
        return if (zone != null) formatted else " UTC"
    }

    internal fun render(
        name: String,
        heading: String,
        values: Map<String, String> = emptyMap(),
        rows: List<Pair<String, String>> = emptyList()
    ): String {
        val filled = values.mapValues { escape(it.value) } + ("rows" to rowsHtml(rows))
        val content = fill(template(name), filled)
        return fill(
            template("layout"),
            mapOf("heading" to escape(heading), "home" to escape(homeUrl), "content" to content)
        )
    }

    private fun rowsHtml(rows: List<Pair<String, String>>) = rows.joinToString("") { (label, value) ->
        fill(template("details-row"), mapOf("label" to escape(label), "value" to escape(value)))
    }

    private fun fill(template: String, values: Map<String, String>) =
        values.entries.fold(template) { filled, (key, value) -> filled.replace("{{$key}}", value) }

    private fun escape(value: String) =
        value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private fun template(name: String) = templates.getOrPut(name) {
        EmailService::class.java.getResourceAsStream("/emails/$name.html")?.bufferedReader()?.use { it.readText() }
            ?: error("Missing email template: $name")
    }

    private suspend fun send(to: String, subject: String, html: String) {
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

    private fun notify(to: String, subject: String, html: String) {
        scope.launch {
            try {
                send(to, subject, html)
            } catch (e: Exception) {
                log.error("Failed to send notification: $subject", e)
            }
        }
    }
}
