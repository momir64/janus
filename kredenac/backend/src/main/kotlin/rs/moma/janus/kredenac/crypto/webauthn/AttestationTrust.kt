package rs.moma.janus.kredenac.crypto.webauthn

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import java.security.cert.CertificateFactory
import kotlinx.coroutines.withTimeoutOrNull
import io.ktor.client.statement.bodyAsText
import java.security.cert.X509Certificate
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory.getLogger
import kotlinx.serialization.json.Json
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.HttpClient
import java.security.PublicKey
import io.ktor.http.isSuccess

// https://developer.android.com/privacy-and-security/security-key-attestation
private const val ROOTS_URL = "https://android.googleapis.com/attestation/root"
private const val STATUS_URL = "https://android.googleapis.com/attestation/status"

private val FIRST_LOAD_WAIT = 10.seconds

@Serializable
class Revocation(val status: String, val reason: String? = null)

@Serializable
private class StatusList(val entries: Map<String, Revocation> = emptyMap())

private val json = Json { ignoreUnknownKeys = true }

internal fun parseRoots(body: String): List<PublicKey> {
    val factory = CertificateFactory.getInstance("X.509")
    val roots = json.decodeFromString<List<String>>(body).map {
        (factory.generateCertificate(it.toByteArray().inputStream()) as X509Certificate).publicKey
    }
    check(roots.isNotEmpty()) { "the attestation root list holds no certificates" }
    return roots
}

internal fun parseRevoked(body: String): Map<String, Revocation> = json.decodeFromString<StatusList>(body).entries

class AttestationTrust {
    private val log = getLogger(AttestationTrust::class.java)
    private val client by lazy { HttpClient(CIO) }
    private val loaded = CompletableDeferred<Unit>()

    @Volatile
    private var lists = Lists(emptyList(), emptyMap())

    class Lists(val roots: List<PublicKey>, val revoked: Map<String, Revocation>) {
        fun revocationOf(certificate: X509Certificate): Revocation? =
            revoked[certificate.serialNumber.toString(16)]
    }

    suspend fun current(): Lists {
        withTimeoutOrNull(FIRST_LOAD_WAIT) { loaded.await() }
        return lists
    }

    suspend fun refresh() {
        try {
            val roots = fetchRoots()
            val revoked = fetchRevoked()
            lists = Lists(roots, revoked)
            log.info("Android attestation lists refreshed: ${roots.size} roots, ${revoked.size} revoked keys")
        } finally {
            loaded.complete(Unit)
        }
    }

    private suspend fun fetchRoots(): List<PublicKey> = parseRoots(fetch(ROOTS_URL))

    private suspend fun fetchRevoked(): Map<String, Revocation> = parseRevoked(fetch(STATUS_URL))

    private suspend fun fetch(url: String): String {
        val response = client.get(url)
        if (!response.status.isSuccess()) throw IllegalStateException("$url answered ${response.status}")
        return response.bodyAsText()
    }

    companion object {
        fun pinned(vararg roots: PublicKey, revoked: Map<String, Revocation> = emptyMap()): AttestationTrust =
            AttestationTrust().apply {
                lists = Lists(roots.toList(), revoked)
                loaded.complete(Unit)
            }
    }
}
