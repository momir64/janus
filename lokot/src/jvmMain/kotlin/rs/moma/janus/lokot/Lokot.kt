package rs.moma.janus.lokot

import rs.moma.janus.lokot.browser.parseUnlockBody
import rs.moma.janus.lokot.browser.challengeJson
import java.util.concurrent.locks.ReentrantLock
import rs.moma.janus.lokot.files.LokotFile
import rs.moma.janus.lokot.externals.wipe
import rs.moma.janus.lokot.files.Dek
import kotlin.concurrent.withLock
import kotlin.io.encoding.Base64
import kotlin.io.path.readBytes
import kotlin.time.Duration
import java.nio.CharBuffer
import java.nio.file.Path

public class Lokot(private val vault: Path) {
    private val file = LokotFile.parse(vault.readBytes())
    private val guard = ReentrantLock()
    private val opened = guard.newCondition()

    private var dek: ByteArray? = null
    private var values: MutableMap<String, CharArray>? = null

    public fun isUnlocked(): Boolean = guard.withLock { values != null }

    public fun challenge(rpId: String, home: String = "/"): Challenge = Challenge(
        rpId = rpId,
        home = home,
        salt = file.header.salt.copyOf(),
        credentialIds = file.header.credentialsFor(rpId).map { it.id.copyOf() },
    )

    private fun unlock(credentialId: ByteArray, prfOutput: ByteArray): Boolean {
        val credential = file.header.credentials.firstOrNull { it.id.contentEquals(credentialId) } ?: return false
        val opening = Dek.unwrap(prfOutput, credential) ?: return false
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
            dek?.wipe()
            dek = opening
            values = body.values.toMutableMap()
            opened.signalAll()
        }
        return true
    }

    public fun unlock(body: String): Boolean {
        val (credentialId, output) = parseUnlockBody(body) ?: return false
        try {
            return unlock(credentialId, output)
        } finally {
            output.wipe()
        }
    }

    /**
     * A copy the caller owns and should blank when it is done. Characters rather than a String
     * because a String cannot be overwritten: it would sit in the heap until the collector
     * happened to come for it, and every substring or concatenation would leave another behind.
     */
    public fun get(name: String): CharArray = guard.withLock {
        val held = values ?: error("$vault is locked")
        (held[name] ?: error("$vault holds no value for $name")).copyOf()
    }

    public fun getBytes(name: String): ByteArray {
        val chars = get(name)
        try {
            return Base64.decode(CharBuffer.wrap(chars))
        } finally {
            chars.wipe()
        }
    }

    public fun lock(): Unit = guard.withLock {
        dek?.wipe()
        dek = null
        values?.values?.forEach { it.wipe() }
        values = null
    }

    /** Blocks until someone unlocks it, so a caller waits rather than polls. */
    public fun awaitUnlock(): Unit = guard.withLock {
        while (values == null) opened.await()
    }

    /** As [awaitUnlock], but gives up. False if it is still locked when the time is out. */
    public fun awaitUnlock(timeout: Duration): Boolean = guard.withLock {
        var remaining = timeout.inWholeNanoseconds
        while (values == null && remaining > 0) remaining = opened.awaitNanos(remaining)
        values != null
    }

    public companion object {
        private const val PAGE = "/lokot/unlock.html"
        private const val DEFAULT_BASE = "/"

        public fun page(base: String = DEFAULT_BASE): String {
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

public class Challenge(
    public val rpId: String,
    public val home: String,
    public val salt: ByteArray,
    public val credentialIds: List<ByteArray>,
) {
    public val json: String get() = challengeJson(rpId, salt, credentialIds, home)
}
