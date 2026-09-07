package rs.moma.janus.lokot.checks

import rs.moma.janus.lokot.externals.Crypto
import rs.moma.janus.lokot.externals.toHex
import rs.moma.janus.lokot.files.*

internal fun formatChecks(): List<Check> {
    val entries = mapOf("PORT" to "8080", "CERT" to PEM, "EMPTY" to "", "ODD" to "a = b \\ c")
    val hmacOutput = ByteArray(32) { (it + 100).toByte() }
    val credentialId = ByteArray(48) { (it + 7).toByte() }
    val kek = ByteArray(Crypto.KEY_SIZE) { it.toByte() }

    val format = CheckGroup(FORMAT_BUG)

    val secondOutput = ByteArray(32) { (it + 200).toByte() }
    val secondId = ByteArray(32) { (it + 11).toByte() }

    fun credential() = Kek.wrap(hmacOutput, credentialId, kek)
    fun secondCredential() = Kek.wrap(secondOutput, secondId, kek)

    fun header(vararg credentials: WrappedCredential) = LokotHeader(
        project = "example",
        salt = ByteArray(LokotHeader.SALT_SIZE) { it.toByte() },
        rpId = "example.com",
        credentials = credentials.toList(),
    )

    val secrets = mapOf("SECRET_A" to "<secret_value>", "SECRET_B" to PEM)
    fun file() = LokotFile.build(header(credential()), secrets, kek)
    fun twoKeyFile() = LokotFile.build(header(credential(), secondCredential()), secrets, kek)
    val freshKek = ByteArray(Crypto.KEY_SIZE) { (it + 64).toByte() }
    fun rekeyedFile() = LokotFile.build(header(Kek.wrap(secondOutput, secondId, freshKek)), secrets, freshKek)

    return listOf(
        format.holds("key=value encoding", "round trips") { PlaintextFile.decode(PlaintextFile.encode(entries)) == entries },
        format.holds("key=value encoding", "multi-line values survive") {
            PlaintextFile.decode(PlaintextFile.encode(mapOf("CERT" to PEM)))["CERT"] == PEM
        },
        format.rejects("key=value encoding", "rejects keys with separators") {
            PlaintextFile.encode(mapOf("a=b" to "x"))
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
        format.holds("kek envelope", "a second key reaches the same kek") {
            Kek.unwrap(secondOutput, secondCredential())?.contentEquals(kek) == true
        },
        format.holds("kek envelope", "one key's envelope does not open another's") {
            Kek.unwrap(hmacOutput, secondCredential()) == null && Kek.unwrap(secondOutput, credential()) == null
        },

        format.holds("add-key", "either enrolled key opens the file") {
            val parsed = LokotFile.parse(twoKeyFile())
            parsed.header.credentials.all { wrapped ->
                val output = if (wrapped.id.contentEquals(credentialId)) hmacOutput else secondOutput
                parsed.open(Kek.unwrap(output, wrapped)!!) == secrets
            }
        },
        format.holds("add-key", "the credentials keep their order and identity") {
            LokotFile.parse(twoKeyFile()).header.credentials.map { it.id.toHex() } ==
                    listOf(credentialId.toHex(), secondId.toHex())
        },
        format.holds("add-key", "a key that was never enrolled opens nothing") {
            LokotFile.parse(twoKeyFile()).header.credentials.all { Kek.unwrap(ByteArray(32), it) == null }
        },

        format.holds("rekey", "only the presented key is left enrolled") {
            val header = LokotFile.parse(rekeyedFile()).header
            header.credentials.singleOrNull()?.id?.contentEquals(secondId) == true
        },
        format.holds("rekey", "the presented key opens the re-keyed file") {
            val rekeyed = LokotFile.parse(rekeyedFile())
            rekeyed.open(Kek.unwrap(secondOutput, rekeyed.header.credentials.single())!!) == secrets
        },
        format.holds("rekey", "the revoked key opens nothing in it") {
            val rekeyed = LokotFile.parse(rekeyedFile())
            rekeyed.header.credentials.all { Kek.unwrap(hmacOutput, it) == null } && rekeyed.open(kek) == null
        },

        format.holds("lokot file", "secrets round trip") { LokotFile.parse(file()).open(kek) == secrets },
        format.holds("lokot file", "salt survives") {
            LokotFile.parse(file()).header.salt.contentEquals(ByteArray(LokotHeader.SALT_SIZE) { it.toByte() })
        },
        format.holds("lokot file", "rpId survives") { LokotFile.parse(file()).header.rpId == "example.com" },
        format.holds("lokot file", "project survives") { LokotFile.parse(file()).header.project == "example" },
        format.holds("lokot file", "credentials survive") {
            LokotFile.parse(file()).header.credentials.single().id.contentEquals(credentialId)
        },
        format.holds("lokot file", "body stays sealed under a wrong kek") {
            LokotFile.parse(file()).open(ByteArray(Crypto.KEY_SIZE)) == null
        },

        format.holds("lokot file", "edited header fails to open") {
            val bytes = file()
            val marker = "example.com".encodeToByteArray()
            val at = bytes.indices.first { start -> marker.indices.all { bytes.getOrNull(start + it) == marker[it] } }
            bytes[at] = (bytes[at].toInt() xor 1).toByte()
            LokotFile.parse(bytes).open(kek) == null
        },

        format.rejects("lokot file", "rejects a corrupted hex field") {
            val bytes = file()
            val nonceStart = bytes.size - Crypto.NONCE_SIZE - Crypto.TAG_SIZE - PlaintextFile.encode(secrets).size
            bytes[nonceStart - 2] = 'z'.code.toByte()  // -1 is the newline that closes the header
            LokotFile.parse(bytes)
        },
        format.rejects("lokot file", "rejects a header that is not framed by newlines") {
            val bytes = file()
            val nonceStart = bytes.size - Crypto.NONCE_SIZE - Crypto.TAG_SIZE - PlaintextFile.encode(secrets).size
            bytes[nonceStart - 1] = 'x'.code.toByte()
            LokotFile.parse(bytes)
        },
        format.holds("lokot file", "the header reads as its own lines") {
            val bytes = file()
            val nonceStart = bytes.size - Crypto.NONCE_SIZE - Crypto.TAG_SIZE - PlaintextFile.encode(secrets).size
            val first = bytes.copyOfRange(12, nonceStart - 1).decodeToString().lineSequence().first()
            first.startsWith("project") && first.endsWith("= example")
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

