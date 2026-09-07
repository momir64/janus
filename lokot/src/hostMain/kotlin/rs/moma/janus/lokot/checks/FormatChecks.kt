package rs.moma.janus.lokot.checks

import rs.moma.janus.lokot.externals.Crypto
import rs.moma.janus.lokot.externals.toHex
import rs.moma.janus.lokot.files.*

internal fun formatChecks(): List<Check> {
    val entries = mapOf("PORT" to "8080", "CERT" to PEM, "EMPTY" to "", "ODD" to "a = b \\ c")
    val secondOutput = ByteArray(32) { (it + 200).toByte() }
    val hmacOutput = ByteArray(32) { (it + 100).toByte() }
    val credentialId = ByteArray(48) { (it + 7).toByte() }
    val kek = ByteArray(Crypto.KEY_SIZE) { it.toByte() }
    val secondId = ByteArray(32) { (it + 11).toByte() }
    val cliRp = "lokot.localhost"

    val format = CheckGroup(FORMAT_BUG)
    val encoding = format.section("key=value encoding")
    val envelope = format.section("kek envelope")
    val addKey = format.section("add-key")
    val rekeying = format.section("rekey")
    val lokotFile = format.section("lokot file")

    fun credential() = Kek.wrap(hmacOutput, credentialId, cliRp, kek)
    fun secondCredential() = Kek.wrap(secondOutput, secondId, cliRp, kek)

    fun header(vararg credentials: WrappedCredential) = LokotHeader(
        project = "example",
        salt = ByteArray(LokotHeader.SALT_SIZE) { it.toByte() },
        credentials = credentials.toList(),
    )

    val secrets = mapOf("SECRET_A" to "<secret_value>", "SECRET_B" to PEM)
    val schema = "project = \"example\"\n[secrets]\nSECRET_A = { type = \"port\" }\n"
    fun body() = VaultBody(schema, secrets)
    val bodySize = body().encode().size
    fun file() = LokotFile.build(header(credential()), body(), kek)
    fun twoKeyFile() = LokotFile.build(header(credential(), secondCredential()), body(), kek)
    val freshKek = ByteArray(Crypto.KEY_SIZE) { (it + 64).toByte() }
    fun rekeyedFile() = LokotFile.build(header(Kek.wrap(secondOutput, secondId, cliRp, freshKek)), body(), freshKek)

    return listOf(
        encoding.holds("round trips"
        ) { PlaintextFile.decode(PlaintextFile.encode(entries)) == entries },
        encoding.holds("multi-line values survive") {
            PlaintextFile.decode(PlaintextFile.encode(mapOf("CERT" to PEM)))["CERT"] == PEM
        },
        encoding.rejects("rejects keys with separators") {
            PlaintextFile.encode(mapOf("a=b" to "x"))
        },

        envelope.holds("unwraps with the right hmac output") {
            Kek.unwrap(hmacOutput, credential())?.contentEquals(kek) == true
        },
        envelope.holds("stays sealed under a wrong hmac output") {
            Kek.unwrap(ByteArray(32), credential()) == null
        },
        envelope.holds("every wrap uses a fresh nonce") {
            !credential().nonce.contentEquals(credential().nonce)
        },
        envelope.holds("a second key reaches the same kek") {
            Kek.unwrap(secondOutput, secondCredential())?.contentEquals(kek) == true
        },
        envelope.holds("one key's envelope does not open another's") {
            Kek.unwrap(hmacOutput, secondCredential()) == null && Kek.unwrap(secondOutput, credential()) == null
        },

        addKey.holds("either enrolled key opens the file") {
            val parsed = LokotFile.parse(twoKeyFile())
            parsed.header.credentials.all { wrapped ->
                val output = if (wrapped.id.contentEquals(credentialId)) hmacOutput else secondOutput
                parsed.open(Kek.unwrap(output, wrapped)!!)?.values == secrets
            }
        },
        addKey.holds("the credentials keep their order and identity") {
            LokotFile.parse(twoKeyFile()).header.credentials.map { it.id.toHex() } ==
                    listOf(credentialId.toHex(), secondId.toHex())
        },
        addKey.holds("a key that was never enrolled opens nothing") {
            LokotFile.parse(twoKeyFile()).header.credentials.all { Kek.unwrap(ByteArray(32), it) == null }
        },

        rekeying.holds("only the presented key is left enrolled") {
            val header = LokotFile.parse(rekeyedFile()).header
            header.credentials.singleOrNull()?.id?.contentEquals(secondId) == true
        },
        rekeying.holds("the presented key opens the re-keyed file") {
            val rekeyed = LokotFile.parse(rekeyedFile())
            rekeyed.open(Kek.unwrap(secondOutput, rekeyed.header.credentials.single())!!)?.values == secrets
        },
        rekeying.holds("the revoked key opens nothing in it") {
            val rekeyed = LokotFile.parse(rekeyedFile())
            rekeyed.header.credentials.all { Kek.unwrap(hmacOutput, it) == null } && rekeyed.open(kek) == null
        },

        lokotFile.holds("secrets round trip") { LokotFile.parse(file()).open(kek)?.values == secrets },
        lokotFile.holds("salt survives") {
            LokotFile.parse(file()).header.salt.contentEquals(ByteArray(LokotHeader.SALT_SIZE) { it.toByte() })
        },
        lokotFile.holds("the schema travels inside the body") {
            LokotFile.parse(file()).open(kek)?.schema == schema
        },
        lokotFile.holds("the schema is not in the plaintext header") {
            !file().decodeToString().contains("[secrets]")
        },
        lokotFile.rejects("rejects a body whose schema runs past its end") {
            VaultBody.decode(byteArrayOf(0, 0, 0, 9, 65))
        },
        lokotFile.holds("the rp id each key was enrolled under survives") {
            LokotFile.parse(file()).header.credentials.single().rpId == cliRp
        },
        lokotFile.holds("a key of another family is not asked for") {
            val browser = Kek.wrap(secondOutput, secondId, "example.com", kek)
            val parsed = LokotFile.parse(LokotFile.build(header(credential(), browser), body(), kek))
            parsed.header.credentialsFor(cliRp).single().id.contentEquals(credentialId) &&
                    parsed.header.credentialsFor("example.com").single().id.contentEquals(secondId)
        },
        lokotFile.holds("project survives") { LokotFile.parse(file()).header.project == "example" },
        lokotFile.holds("credentials survive") {
            LokotFile.parse(file()).header.credentials.single().id.contentEquals(credentialId)
        },
        lokotFile.holds("body stays sealed under a wrong kek") {
            LokotFile.parse(file()).open(ByteArray(Crypto.KEY_SIZE)) == null
        },

        lokotFile.holds("edited header fails to open") {
            val bytes = file()
            val marker = cliRp.encodeToByteArray()
            val at = bytes.indices.first { start -> marker.indices.all { bytes.getOrNull(start + it) == marker[it] } }
            bytes[at] = (bytes[at].toInt() xor 1).toByte()
            LokotFile.parse(bytes).open(kek) == null
        },

        lokotFile.rejects("rejects a corrupted hex field") {
            val bytes = file()
            val nonceStart = bytes.size - Crypto.NONCE_SIZE - Crypto.TAG_SIZE - bodySize
            bytes[nonceStart - 2] = 'z'.code.toByte()  // -1 is the newline that closes the header
            LokotFile.parse(bytes)
        },
        lokotFile.rejects("rejects a header that is not framed by newlines") {
            val bytes = file()
            val nonceStart = bytes.size - Crypto.NONCE_SIZE - Crypto.TAG_SIZE - bodySize
            bytes[nonceStart - 1] = 'x'.code.toByte()
            LokotFile.parse(bytes)
        },
        lokotFile.holds("the header reads as its own lines") {
            val bytes = file()
            val nonceStart = bytes.size - Crypto.NONCE_SIZE - Crypto.TAG_SIZE - bodySize
            val first = bytes.copyOfRange(12, nonceStart - 1).decodeToString().lineSequence().first()
            first.startsWith("project") && first.endsWith("= example")
        },

        lokotFile.rejects("rejects an unknown format version") {
            LokotFile.parse(file().also { it[6] = (LATEST_LOKOT_FORMAT_VERSION + 1).toByte() })
        },
        lokotFile.rejects("rejects a file that is not a lokot file") {
            LokotFile.parse(file().also { it[0] = 'X'.code.toByte() })
        },
        lokotFile.rejects("rejects a truncated file") { LokotFile.parse(file().copyOfRange(0, 8)) },

        lokotFile.holds("every write uses a fresh nonce") { !file().contentEquals(file()) },
    )
}

