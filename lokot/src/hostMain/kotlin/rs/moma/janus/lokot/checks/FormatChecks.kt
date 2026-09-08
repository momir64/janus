package rs.moma.janus.lokot.checks

import rs.moma.janus.lokot.externals.Crypto
import rs.moma.janus.lokot.externals.toHex
import rs.moma.janus.lokot.files.*

internal fun formatChecks(): List<Check> {
    val entries = mapOf("PORT" to "8080", "CERT" to PEM, "EMPTY" to "", "ODD" to "a = b \\ c")
    val secondOutput = ByteArray(32) { (it + 200).toByte() }
    val hmacOutput = ByteArray(32) { (it + 100).toByte() }
    val credentialId = ByteArray(48) { (it + 7).toByte() }
    val dek = ByteArray(Crypto.KEY_SIZE) { it.toByte() }
    val secondId = ByteArray(32) { (it + 11).toByte() }
    val cliRp = "lokot.localhost"

    val format = CheckGroup(FORMAT_BUG)
    val encoding = format.section("key=value encoding")
    val envelope = format.section("dek envelope")
    val addKey = format.section("add-key")
    val rekeying = format.section("rekey")
    val lokotFile = format.section("lokot file")

    fun credential() = Dek.wrap(hmacOutput, credentialId, cliRp, dek)
    fun secondCredential() = Dek.wrap(secondOutput, secondId, cliRp, dek)

    fun header(vararg credentials: WrappedCredential) = LokotHeader(
        project = "example",
        salt = ByteArray(LokotHeader.SALT_SIZE) { it.toByte() },
        credentials = credentials.toList(),
    )

    val secrets = mapOf("SECRET_A" to "<secret_value>", "SECRET_B" to PEM)
    val schema = "project = \"example\"\n[secrets]\nSECRET_A = { type = \"port\" }\n"
    fun body() = VaultBody(schema, secrets.asChars())
    val bodySize = body().encode().size
    fun file() = LokotFile.build(header(credential()), body(), dek)
    fun twoKeyFile() = LokotFile.build(header(credential(), secondCredential()), body(), dek)
    val freshDek = ByteArray(Crypto.KEY_SIZE) { (it + 64).toByte() }
    fun rekeyedFile() = LokotFile.build(header(Dek.wrap(secondOutput, secondId, cliRp, freshDek)), body(), freshDek)

    return listOf(
        encoding.holds("round trips"
        ) { PlaintextFile.decode(PlaintextFile.encode(entries)).asText() == entries },
        encoding.holds("multi-line values survive") {
            PlaintextFile.decode(PlaintextFile.encode(mapOf("CERT" to PEM))).asText()["CERT"] == PEM
        },
        encoding.rejects("rejects keys with separators") {
            PlaintextFile.encode(mapOf("a=b" to "x"))
        },

        envelope.holds("unwraps with the right hmac output") {
            Dek.unwrap(hmacOutput, credential())?.contentEquals(dek) == true
        },
        envelope.holds("stays sealed under a wrong hmac output") {
            Dek.unwrap(ByteArray(32), credential()) == null
        },
        envelope.holds("every wrap uses a fresh nonce") {
            !credential().nonce.contentEquals(credential().nonce)
        },
        envelope.holds("a second key reaches the same dek") {
            Dek.unwrap(secondOutput, secondCredential())?.contentEquals(dek) == true
        },
        envelope.holds("one key's envelope does not open another's") {
            Dek.unwrap(hmacOutput, secondCredential()) == null && Dek.unwrap(secondOutput, credential()) == null
        },

        addKey.holds("either enrolled key opens the file") {
            val parsed = LokotFile.parse(twoKeyFile())
            parsed.header.credentials.all { wrapped ->
                val output = if (wrapped.id.contentEquals(credentialId)) hmacOutput else secondOutput
                parsed.open(Dek.unwrap(output, wrapped)!!)?.values?.asText() == secrets
            }
        },
        addKey.holds("the credentials keep their order and identity") {
            LokotFile.parse(twoKeyFile()).header.credentials.map { it.id.toHex() } ==
                    listOf(credentialId.toHex(), secondId.toHex())
        },
        addKey.holds("a key that was never enrolled opens nothing") {
            LokotFile.parse(twoKeyFile()).header.credentials.all { Dek.unwrap(ByteArray(32), it) == null }
        },

        rekeying.holds("only the presented key is left enrolled") {
            val header = LokotFile.parse(rekeyedFile()).header
            header.credentials.singleOrNull()?.id?.contentEquals(secondId) == true
        },
        rekeying.holds("the presented key opens the re-keyed file") {
            val rekeyed = LokotFile.parse(rekeyedFile())
            rekeyed.open(Dek.unwrap(secondOutput, rekeyed.header.credentials.single())!!)?.values?.asText() == secrets
        },
        rekeying.holds("the revoked key opens nothing in it") {
            val rekeyed = LokotFile.parse(rekeyedFile())
            rekeyed.header.credentials.all { Dek.unwrap(hmacOutput, it) == null } && rekeyed.open(dek) == null
        },

        lokotFile.holds("secrets round trip") { LokotFile.parse(file()).open(dek)?.values?.asText() == secrets },
        lokotFile.holds("salt survives") {
            LokotFile.parse(file()).header.salt.contentEquals(ByteArray(LokotHeader.SALT_SIZE) { it.toByte() })
        },
        lokotFile.holds("the schema travels inside the body") {
            LokotFile.parse(file()).open(dek)?.schema == schema
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
            val browser = Dek.wrap(secondOutput, secondId, "example.com", dek)
            val parsed = LokotFile.parse(LokotFile.build(header(credential(), browser), body(), dek))
            parsed.header.credentialsFor(cliRp).single().id.contentEquals(credentialId) &&
                    parsed.header.credentialsFor("example.com").single().id.contentEquals(secondId)
        },
        lokotFile.holds("project survives") { LokotFile.parse(file()).header.project == "example" },
        lokotFile.holds("credentials survive") {
            LokotFile.parse(file()).header.credentials.single().id.contentEquals(credentialId)
        },
        lokotFile.holds("body stays sealed under a wrong dek") {
            LokotFile.parse(file()).open(ByteArray(Crypto.KEY_SIZE)) == null
        },

        lokotFile.holds("edited header fails to open") {
            val bytes = file()
            val marker = cliRp.encodeToByteArray()
            val at = bytes.indices.first { start -> marker.indices.all { bytes.getOrNull(start + it) == marker[it] } }
            bytes[at] = (bytes[at].toInt() xor 1).toByte()
            LokotFile.parse(bytes).open(dek) == null
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

