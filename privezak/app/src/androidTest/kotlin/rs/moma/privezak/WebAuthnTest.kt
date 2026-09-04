package rs.moma.privezak

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import rs.moma.privezak.provider.attestationObject
import rs.moma.privezak.provider.authenticatorData
import rs.moma.privezak.security.PasskeyStore
import android.content.ContextWrapper
import java.security.MessageDigest
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.junit.Test
import java.io.File

@RunWith(AndroidJUnit4::class)
class WebAuthnTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    private val context = object : ContextWrapper(target) {
        private val dir = File(baseContext.cacheDir, "webauthn-test").apply { mkdirs() }
        override fun getFilesDir() = dir
    }

    private val dataKey = ByteArray(32) { it.toByte() }
    private val rpId = "example.com"

    @Test
    fun buildsAttestedAuthenticatorData() {
        val store = PasskeyStore(context, dataKey)
        val passkey = store.create(rpId, "Example", byteArrayOf(1, 2, 3), "test", "Test")
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
            assertArrayEquals("aaguid is all zeroes", ByteArray(16), data.copyOfRange(37, 53))
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
    fun wrapsAuthenticatorDataInANoneAttestationObject() {
        val data = authenticatorData(rpId, 0)
        val attestation = attestationObject(data)

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
