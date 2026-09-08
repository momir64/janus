package rs.moma.janus.lokot

import java.util.concurrent.locks.ReentrantLock
import rs.moma.janus.lokot.files.LokotFile
import rs.moma.janus.lokot.externals.wipe
import rs.moma.janus.lokot.files.Kek
import kotlin.concurrent.withLock
import kotlin.io.encoding.Base64
import kotlin.io.path.readBytes
import kotlin.time.Duration
import java.nio.CharBuffer
import java.nio.file.Path

private val urlEncoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
private val urlDecoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

class Lokot(private val vault: Path) {
    private val file = LokotFile.parse(vault.readBytes())
    private val guard = ReentrantLock()
    private val opened = guard.newCondition()

    private var kek: ByteArray? = null
    private var values: MutableMap<String, CharArray>? = null

    fun isUnlocked(): Boolean = guard.withLock { values != null }

    fun challenge(rpId: String): Challenge = Challenge(
        rpId = rpId,
        salt = file.header.salt.copyOf(),
        credentialIds = file.header.credentialsFor(rpId).map { it.id.copyOf() },
    )

    private fun unlock(credentialId: ByteArray, prfOutput: ByteArray): Boolean {
        val credential = file.header.credentials.firstOrNull { it.id.contentEquals(credentialId) } ?: return false
        val opening = Kek.unwrap(prfOutput, credential) ?: return false
        val body = try {
            file.open(opening)
        } catch (_: Exception) {
            null
        }
        if (body == null) {
            opening.wipe()
            return false
        }

        guard.withLock {
            kek?.wipe()
            kek = opening
            values = body.values.toMutableMap()
            opened.signalAll()
        }
        return true
    }

    fun unlock(body: String): Boolean {
        if (body.length > MAX_BODY) return false
        val credentialId = field(body, "credentialId") ?: return false
        val output = decode(field(body, "output") ?: return false) ?: return false
        try {
            return unlock(decode(credentialId) ?: return false, output)
        } finally {
            output.wipe()
        }
    }

    /**
     * A copy the caller owns and should blank when it is done. Characters rather than a String
     * because a String cannot be overwritten: it would sit in the heap until the collector
     * happened to come for it, and every substring or concatenation would leave another behind.
     */
    fun get(name: String): CharArray = guard.withLock {
        val held = values ?: error("$vault is locked")
        (held[name] ?: error("$vault holds no value for $name")).copyOf()
    }

    fun getBytes(name: String): ByteArray {
        val chars = get(name)
        try {
            return Base64.decode(CharBuffer.wrap(chars))
        } finally {
            chars.wipe()
        }
    }

    fun clear(name: String) = guard.withLock { values?.remove(name)?.wipe(); Unit }

    fun lock() = guard.withLock {
        kek?.wipe()
        kek = null
        values?.values?.forEach { it.wipe() }
        values = null
    }

    /** Blocks until someone unlocks it, so a caller waits rather than polls. */
    fun awaitUnlock() = guard.withLock {
        while (values == null) opened.await()
    }

    /** As [awaitUnlock], but gives up. False if it is still locked when the time is out. */
    fun awaitUnlock(timeout: Duration): Boolean = guard.withLock {
        var remaining = timeout.inWholeNanoseconds
        while (values == null && remaining > 0) remaining = opened.awaitNanos(remaining)
        values != null
    }

    private fun field(body: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"([A-Za-z0-9_-]{1,4096})\"").find(body)?.groupValues?.get(1)

    private fun decode(text: String): ByteArray? = runCatching { urlDecoder.decode(text) }.getOrNull()

    companion object {
        private const val PAGE = "/lokot/unlock.html"
        private const val DEFAULT_BASE = "/lokot/"
        private const val MAX_BODY = 16 * 1024

        fun page(base: String = DEFAULT_BASE): String {
            require(base.startsWith("/") && base.none { it == '"' || it.isWhitespace() }) {
                "the base has to be a path, like $DEFAULT_BASE, not '$base'"
            }
            val page = Lokot::class.java.getResource(PAGE)?.readText() ?: error("$PAGE is missing from the jar")
            val marker = "<base href=\"$DEFAULT_BASE\">"
            check(marker in page) { "$PAGE has no $marker to point at where it is served from" }
            return page.replace(marker, "<base href=\"${base.removeSuffix("/")}/\">")
        }
    }
}

class Challenge(val rpId: String, val salt: ByteArray, val credentialIds: List<ByteArray>) {
    val json: String
        get() = """{"rpId":"$rpId","salt":"${salt.url()}","credentialIds":[${
            credentialIds.joinToString(",") { "\"${it.url()}\"" }
        }]}"""

    private fun ByteArray.url() = urlEncoder.encode(this)
}
