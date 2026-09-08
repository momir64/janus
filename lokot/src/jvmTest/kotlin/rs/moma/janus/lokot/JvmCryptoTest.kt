package rs.moma.janus.lokot

import rs.moma.janus.lokot.files.PlaintextFile
import rs.moma.janus.lokot.externals.fromHex
import rs.moma.janus.lokot.files.LokotHeader
import rs.moma.janus.lokot.externals.Crypto
import rs.moma.janus.lokot.externals.toHex
import rs.moma.janus.lokot.files.LokotFile
import rs.moma.janus.lokot.files.VaultBody
import rs.moma.janus.lokot.files.asChars
import rs.moma.janus.lokot.files.asText
import rs.moma.janus.lokot.files.Kek
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.Test

class JvmCryptoTest {
    private val key = "0b".repeat(32).fromHex()
    private val nonce = "00".repeat(12).fromHex()
    private val aad = "lokot header".encodeToByteArray()
    private val message = "the quick brown fox"

    @Test
    fun `sha-256 matches`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Crypto.sha256(ByteArray(0)).toHex(),
        )
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Crypto.sha256("abc".encodeToByteArray()).toHex(),
        )
    }

    @Test
    fun `hmac matches rfc 4231`() = assertEquals(
        "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7",
        Crypto.hmacSha256("0b".repeat(20).fromHex(), "Hi There".encodeToByteArray()).toHex(),
    )

    @Test
    fun `hkdf matches rfc 5869 case 1`() = assertEquals(
        "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865",
        Crypto.hkdf(
            ikm = "0b".repeat(22).fromHex(),
            salt = "000102030405060708090a0b0c".fromHex(),
            info = "f0f1f2f3f4f5f6f7f8f9".fromHex(),
            length = 42,
        ).toHex(),
    )

    @Test
    fun `aes-gcm matches the native output byte for byte`() = assertEquals(
        "5cb224d3f0eee5c2b990be6f52afdc3cdb37d3ec89c985779415b43761f3aa41e16cf1",
        Crypto.aesGcmSeal(key, nonce, message.encodeToByteArray(), aad).toHex(),
    )

    @Test
    fun `aes-gcm refuses what it should`() {
        val sealed = Crypto.aesGcmSeal(key, nonce, message.encodeToByteArray(), aad)
        assertEquals(message, Crypto.aesGcmOpen(key, nonce, sealed, aad)?.decodeToString())
        assertNull(Crypto.aesGcmOpen(key, nonce, sealed, "lokot Selftest".encodeToByteArray()))
        assertNull(Crypto.aesGcmOpen("01".repeat(32).fromHex(), nonce, sealed, aad))
        assertNull(Crypto.aesGcmOpen(key, nonce, sealed.also { it[0] = (it[0].toInt() xor 1).toByte() }, aad))
    }

    @Test
    fun `a vault opens with the key its credential wraps`() {
        val hmacOutput = ByteArray(32) { (it + 100).toByte() }
        val credentialId = ByteArray(48) { (it + 7).toByte() }
        val kek = ByteArray(Crypto.KEY_SIZE) { it.toByte() }
        val secrets = mapOf(
            "JWT_SECRET" to "value",
            "CERT" to "-----BEGIN CERTIFICATE-----\nx\n-----END CERTIFICATE-----"
        )

        val header = LokotHeader(
            project = "example",
            salt = ByteArray(LokotHeader.SALT_SIZE) { it.toByte() },
            credentials = listOf(Kek.wrap(hmacOutput, credentialId, "lokot.localhost", kek)),
        )
        val parsed = LokotFile.parse(
            LokotFile.build(header, VaultBody("project = \"example\"", secrets.asChars()), kek)
        )

        assertEquals("example", parsed.header.project)
        assertEquals("lokot.localhost", parsed.header.credentials.single().rpId)

        val unwrapped = Kek.unwrap(hmacOutput, parsed.header.credentials.single())
        assertTrue(unwrapped.contentEquals(kek))
        assertEquals(secrets, parsed.open(unwrapped!!)?.values?.asText())
        assertNull(parsed.open(ByteArray(Crypto.KEY_SIZE)))
    }

    @Test
    fun `the document format round trips`() {
        val entries = mapOf("PORT" to "8080", "EMPTY" to "", "ODD" to "a = b \\ c")
        assertEquals(entries, PlaintextFile.decode(PlaintextFile.encode(entries)).asText())
    }
}
