package rs.moma.janus.privezak

import androidx.test.platform.app.InstrumentationRegistry
import rs.moma.janus.privezak.provider.authenticatorData
import rs.moma.janus.privezak.provider.decodeBase64url
import rs.moma.janus.privezak.provider.noneAttestation
import androidx.test.ext.junit.runners.AndroidJUnit4
import rs.moma.janus.privezak.security.PasskeyStore
import rs.moma.janus.privezak.provider.prfResults
import rs.moma.janus.privezak.provider.base64url
import kotlinx.serialization.json.jsonPrimitive
import rs.moma.janus.privezak.provider.prfSalt
import android.content.ContextWrapper
import java.security.MessageDigest
import org.junit.runner.RunWith
import org.junit.Assert.*
import kotlin.uuid.Uuid
import org.junit.Test
import java.io.File

@RunWith(AndroidJUnit4::class)
class WebAuthnTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    private val context = object : ContextWrapper(target) {
        private val dir = File(baseContext.cacheDir, "webauthn-test").apply { mkdirs() }
        override fun getFilesDir() = dir
    }

    private val challenge = ByteArray(32) { it.toByte() }
    private val dataKey = ByteArray(32) { it.toByte() }
    private val rpId = "example.com"

    @Test
    fun buildsAttestedAuthenticatorData() {
        val aaguid = "480642ef-fdd8-4fed-bbe8-e90aa0a782ff"
        val store = PasskeyStore(context, dataKey)
        val passkey = store.create(rpId, "Example", byteArrayOf(1, 2, 3), "test", "Test", challenge)
        try {
            val credentialId = ByteArray(16) { it.toByte() }
            val data = authenticatorData(rpId, 0, credentialId, store.publicKey(passkey.id))

            assertArrayEquals(
                "rpIdHash",
                MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray()),
                data.copyOf(32)
            )

            // user present, user verified, attested credential data
            assertEquals(0x45.toByte(), data[32])
            assertArrayEquals("sign count", ByteArray(4), data.copyOfRange(33, 37))
            assertEquals("aaguid", aaguid, Uuid.fromByteArray(data.copyOfRange(37, 53)).toString())
            assertEquals("credential id length", 16, (data[53].toInt() shl 8) or data[54].toInt())
            assertArrayEquals(credentialId, data.copyOfRange(55, 71))

            // COSE_Key: 5-entry map, kty EC2, alg ES256, crv P-256, then 32-byte coordinates
            val cose = data.copyOfRange(71, data.size)
            assertArrayEquals(
                byteArrayOf(0xA5.toByte(), 0x01, 0x02, 0x03, 0x26, 0x20, 0x01, 0x21, 0x58, 0x20),
                cose.copyOf(10)
            )
            assertEquals(0x22.toByte(), cose[42])
            assertEquals(0x58.toByte(), cose[43])
            assertEquals(0x20.toByte(), cose[44])
            assertEquals("total COSE key size", 77, cose.size)
        } finally {
            store.delete(passkey.id)
        }
    }

    @Test
    fun derivesThePrfSaltUnderTheWebAuthnPrefix() {
        val input = ByteArray(32) { it.toByte() }
        assertEquals(
            "dc1f4f3b3d759586245d5de7e2e115d2a056a2df27109db7cef9b4b3a89bb106",
            prfSalt(input.base64url(), alreadyHashed = false).hex()
        )
        assertEquals(
            "a salt the client already derived is used verbatim",
            input.hex(), prfSalt(input.base64url(), alreadyHashed = true).hex()
        )
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test
    fun evaluatesPrfInputsAgainstTheCredentialSecret() {
        val store = PasskeyStore(context, dataKey)
        val passkey = store.create(rpId, "Example", byteArrayOf(1), "test", "Test", challenge)
        val request = """{"extensions":{"prf":{"eval":{"first":"AAEC","second":"AQID"}}}}"""
        fun evaluate() = prfResults(request, passkey.id) { store.hmacSecret(passkey.id, it) }
        try {
            val results = checkNotNull(evaluate()) { "no prf results" }
            val first = results["first"]!!.jsonPrimitive.content
            val second = results["second"]!!.jsonPrimitive.content

            assertEquals("an output is a full HMAC", 32, first.decodeBase64url().size)
            assertNotEquals("both inputs derived the same output", first, second)
            assertEquals("not deterministic", first, evaluate()!!["first"]!!.jsonPrimitive.content)
        } finally {
            store.delete(passkey.id)
        }
    }

    @Test
    fun wrapsAuthenticatorDataInANoneAttestationObject() {
        val data = authenticatorData(rpId, 0)
        val attestation = noneAttestation(data)

        // map(3), "fmt": "none", "attStmt": map(0), "authData": bytes(37)
        assertArrayEquals(
            byteArrayOf(
                0xA3.toByte(), 0x63, 'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(),
                0x64, 'n'.code.toByte(), 'o'.code.toByte(), 'n'.code.toByte(), 'e'.code.toByte()
            ),
            attestation.copyOf(10)
        )
        assertEquals("authData without attestation is 37 bytes", 37, data.size)
        // user present and verified, but no attested credential data this time
        assertEquals(0x05.toByte(), data[32])
    }

    // The counter an assertion reports is the one a relying party compares against the last it
    // saw, so a byte order slip here reads as a cloned authenticator rather than as a bug.
    @Test
    fun encodesTheSignCountBigEndian() {
        val data = authenticatorData(rpId, 0x01020304)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), data.copyOfRange(33, 37))
    }
}
