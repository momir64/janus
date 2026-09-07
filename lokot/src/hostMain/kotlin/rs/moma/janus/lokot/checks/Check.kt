package rs.moma.janus.lokot.checks

import rs.moma.janus.lokot.externals.toHex

// test vectors for HMAC-SHA-256: https://datatracker.ietf.org/doc/html/rfc4231
// test vectors for HKDF-SHA-256: https://datatracker.ietf.org/doc/html/rfc5869
// test values for AES-256-GCM calculated with: https://emn178.github.io/online-tools/aes/encrypt/

// Used both for Gradle tests and for the CLI selftest.
class Check(
    val section: String,
    val name: String,
    val remedy: String? = null,
    private val body: () -> String?,
) {
    fun run(): String? = try {
        body()
    } catch (failure: Throwable) {
        "threw ${failure::class.simpleName}: ${failure.message}"
    }
}

internal class CheckGroup(private val remedy: String) {
    fun equal(section: String, name: String, expected: String, actual: () -> ByteArray) = Check(section, name, remedy) {
        val got = actual().toHex()
        if (got == expected.lowercase()) null else "expected $expected, got $got"
    }

    fun <T> equals(section: String, name: String, expected: T, actual: () -> T) = Check(section, name, remedy) {
        val got = actual()
        if (got == expected) null else "expected $expected, got $got"
    }

    fun holds(section: String, name: String, condition: () -> Boolean) = Check(section, name, remedy) {
        if (condition()) null else "condition did not hold"
    }

    fun rejects(section: String, name: String, block: () -> Unit) = Check(section, name, remedy) {
        try {
            block()
            "accepted input it should have rejected"
        } catch (_: Throwable) {
            null
        }
    }
}

internal const val LATEST_LOKOT_FORMAT_VERSION = 1

internal const val PROJECTLESS = "[secrets]\nA = { type = \"port\" }"
internal const val PEM = "-----BEGIN CERTIFICATE-----\ntest certificate\n-----END CERTIFICATE-----"

internal const val FORMAT_BUG = "There's a code bug in the lokot file format, not an environment issue."
internal const val SCHEMA_BUG = "There's a code bug in the toml parser or schema, not an environment issue."
internal const val CRYPTO_BUG =
    "crypto backend disagrees with the reference implementation - check the linked libssl/libcrypto version"
internal const val NONCE_BUG =
    "nonce generation may be broken or seeded deterministically - do not use this build to encrypt real secrets"

fun allChecks(): List<Check> = cryptoChecks() + formatChecks() + documentChecks() + schemaChecks() + editorChecks()
