package rs.moma.janus.lokot

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

private class CheckGroup(private val remedy: String) {
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

private const val LATEST_LOKOT_FORMAT_VERSION = 1

private const val PEM = "-----BEGIN CERTIFICATE-----\ntest certificate\n-----END CERTIFICATE-----"

private const val FORMAT_BUG = "There's a code bug in the lokot file format, not an environment issue."
private const val CRYPTO_BUG =
    "crypto backend disagrees with the reference implementation - check the linked libssl/libcrypto version"
private const val NONCE_BUG =
    "nonce generation may be broken or seeded deterministically - do not use this build to encrypt real secrets"

fun allChecks(): List<Check> = cryptoChecks() + formatChecks()

private fun cryptoChecks(): List<Check> {
    val cryptoBug = CheckGroup(CRYPTO_BUG)
    val nonceBug = CheckGroup(NONCE_BUG)

    val message = "the quick brown fox"
    val key = "0b".repeat(32).fromHex()
    val nonce = "00".repeat(12).fromHex()
    val aad = "lokot header".encodeToByteArray()

    fun sealed() = Crypto.aesGcmSeal(key, nonce, message.encodeToByteArray(), aad)
    fun byteRange(range: IntRange) = ByteArray(range.count()) { range.first.plus(it).toByte() }

    return listOf(
        cryptoBug.equal("SHA-256", "empty", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855") {
            Crypto.sha256(ByteArray(0))
        },
        cryptoBug.equal("SHA-256", "abc", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad") {
            Crypto.sha256("abc".encodeToByteArray())
        },

        cryptoBug.equal(
            "HMAC-SHA-256 (RFC 4231)", "test", "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"
        ) {
            Crypto.hmacSha256("0b".repeat(20).fromHex(), "Hi There".encodeToByteArray())
        },

        cryptoBug.equal(
            "HKDF-SHA-256 (RFC 5869)",
            "case 1",
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"
        ) {
            Crypto.hkdf(
                ikm = "0b".repeat(22).fromHex(),
                salt = "000102030405060708090a0b0c".fromHex(),
                info = "f0f1f2f3f4f5f6f7f8f9".fromHex(),
                length = 42,
            )
        },
        nonceBug.equal(
            "HKDF-SHA-256 (RFC 5869)",
            "case 2 (long inputs, multi-block expand)",
            "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41" +
                    "c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87"
        ) {
            Crypto.hkdf(
                ikm = byteRange(0x00..0x4f),
                salt = byteRange(0x60..0xaf),
                info = byteRange(0xb0..0xff),
                length = 82,
            )
        },
        nonceBug.equals(
            "HKDF-SHA-256 (RFC 5869)",
            "case 3 (empty salt and info)",
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"
        ) {
            Crypto.hkdf(ikm = "0b".repeat(22).fromHex(), salt = ByteArray(0), info = ByteArray(0), length = 42)
        },

        cryptoBug.equal(
            "AES-256-GCM",
            "matches across crypto implementations",
            "c8803db705c088f6004e7dc806a0b029692b07bc740711213d7f353a920d87537795e4"
        ) { sealed() },

        cryptoBug.equals("AES-256-GCM", "tag appended", message.length + Crypto.TAG_SIZE) { sealed().size },
        cryptoBug.holds("AES-256-GCM", "round trip") {
            Crypto.aesGcmOpen(key, nonce, sealed(), aad)?.decodeToString() == message
        },
        cryptoBug.holds("AES-256-GCM", "empty plaintext round trips") {
            Crypto.aesGcmOpen(key, nonce, Crypto.aesGcmSeal(key, nonce, ByteArray(0), aad), aad)?.isEmpty() == true
        },
        cryptoBug.holds("AES-256-GCM", "rejects tampered ciphertext") {
            val broken = sealed().also { it[0] = (it[0].toInt() xor 1).toByte() }
            Crypto.aesGcmOpen(key, nonce, broken, aad) == null
        },
        cryptoBug.holds("AES-256-GCM", "rejects tampered tag") {
            val broken = sealed().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
            Crypto.aesGcmOpen(key, nonce, broken, aad) == null
        },
        cryptoBug.holds("AES-256-GCM", "rejects modified aad") {
            Crypto.aesGcmOpen(key, nonce, sealed(), "lokot Selftest".encodeToByteArray()) == null
        },
        cryptoBug.holds("AES-256-GCM", "rejects wrong key") {
            Crypto.aesGcmOpen("01".repeat(32).fromHex(), nonce, sealed(), aad) == null
        },
        cryptoBug.holds("AES-256-GCM", "rejects wrong nonce") {
            Crypto.aesGcmOpen(key, "01".repeat(12).fromHex(), sealed(), aad) == null
        },

        cryptoBug.equals("random", "returns the requested length", 48) { Crypto.randomBytes(48).size },
        cryptoBug.holds("random", "does not repeat") {
            !Crypto.randomBytes(32).contentEquals(Crypto.randomBytes(32))
        }
    )
}

private fun formatChecks(): List<Check> {
    val entries = mapOf("PORT" to "8080", "CERT" to PEM, "EMPTY" to "", "ODD" to "a = b \\ c")
    val hmacOutput = ByteArray(32) { (it + 100).toByte() }
    val credentialId = ByteArray(48) { (it + 7).toByte() }
    val kek = ByteArray(Crypto.KEY_SIZE) { it.toByte() }

    val format = CheckGroup(FORMAT_BUG)

    fun credential() = Kek.wrap(hmacOutput, credentialId, kek)

    fun header(credential: WrappedCredential) = LokotHeader(
        salt = ByteArray(LokotHeader.SALT_SIZE) { it.toByte() },
        rpId = "example.com",
        credentials = listOf(credential),
        plain = mapOf("PORT" to "8080", "RP_ID" to "rpid.example"),
    )

    val secrets = mapOf("SECRET_A" to "<secret_value>", "SECRET_B" to PEM)
    fun file() = LokotFile.build(header(credential()), secrets, kek)

    return listOf(
        format.holds("key=value encoding", "round trips") { KeyValue.decode(KeyValue.encode(entries)) == entries },
        format.holds("key=value encoding", "multi-line values survive") {
            KeyValue.decode(KeyValue.encode(mapOf("CERT" to PEM)))["CERT"] == PEM
        },
        format.rejects("key=value encoding", "rejects keys with separators") {
            KeyValue.encode(mapOf("a=b" to "x"))
        },

        format.holds("kek envelope", "unwraps with the right hmac output") {
            Kek.unwrap(hmacOutput, credential())?.contentEquals(kek) == true
        },
        format.holds("kek envelope", "stays sealed under a wrong hmac output") {
            Kek.unwrap(ByteArray(32), credential()) == null
        },
        format.holds("kek envelope", "every wrap uses a fresh nonce") {
            !credential().nonce.contentEquals(credential().nonce)
        },

        format.holds("lokot file", "secrets round trip") { LokotFile.parse(file()).open(kek) == secrets },
        format.holds("lokot file", "salt survives") {
            LokotFile.parse(file()).header.salt.contentEquals(ByteArray(LokotHeader.SALT_SIZE) { it.toByte() })
        },
        format.holds("lokot file", "rpId survives") { LokotFile.parse(file()).header.rpId == "example.com" },
        format.holds("lokot file", "plain values survive") {
            LokotFile.parse(file()).header.plain == mapOf("PORT" to "8080", "RP_ID" to "rpid.example")
        },
        format.holds("lokot file", "credentials survive") {
            LokotFile.parse(file()).header.credentials.single().id.contentEquals(credentialId)
        },
        format.holds("lokot file", "body stays sealed under a wrong kek") {
            LokotFile.parse(file()).open(ByteArray(Crypto.KEY_SIZE)) == null
        },

        format.holds("lokot file", "edited header fails to open") {
            val bytes = file()
            val headerEnd = bytes.size - Crypto.NONCE_SIZE - Crypto.TAG_SIZE - KeyValue.encode(secrets).size
            bytes[headerEnd - 1] = (bytes[headerEnd - 1].toInt() xor 1).toByte()
            LokotFile.parse(bytes).open(kek) == null
        },

        format.rejects("lokot file", "rejects an unknown format version") {
            LokotFile.parse(file().also { it[6] = (LATEST_LOKOT_FORMAT_VERSION + 1).toByte() })
        },
        format.rejects("lokot file", "rejects a file that is not a lokot file") {
            LokotFile.parse(file().also { it[0] = 'X'.code.toByte() })
        },
        format.rejects("lokot file", "rejects a truncated file") { LokotFile.parse(file().copyOfRange(0, 8)) },

        format.holds("lokot file", "every write uses a fresh nonce") { !file().contentEquals(file()) },
    )
}
