package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.crypto.webauthn.AttestationTrust
import rs.moma.janus.kredenac.crypto.webauthn.parseRevoked
import rs.moma.janus.kredenac.crypto.webauthn.Revocation
import rs.moma.janus.kredenac.crypto.webauthn.parseRoots
import rs.moma.janus.kredenac.utils.KeyAttestation
import java.security.cert.CertificateFactory
import java.security.spec.ECGenParameterSpec
import java.security.cert.X509Certificate
import java.security.KeyPairGenerator
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFailsWith
import kotlin.io.encoding.Base64
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.math.BigInteger
import kotlin.test.Test

class AttestationTrustTest {
    private val serial = BigInteger("c8966fcb2fbb0d7a", 16)
    private val attestation = KeyAttestation(serial = serial)

    private val attestedKey = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair().public.encoded
    }

    private val factory = CertificateFactory.getInstance("X.509")

    private val certificates: List<X509Certificate> by lazy {
        attestation.chain(attestedKey, ByteArray(32)).map {
            factory.generateCertificate(it.inputStream()) as X509Certificate
        }
    }

    private fun pem(certificate: X509Certificate) = "-----BEGIN CERTIFICATE-----\n" +
            Base64.encode(certificate.encoded).chunked(64).joinToString("\n") +
            "\n-----END CERTIFICATE-----\n"

    @Test
    fun `reads the keys out of the published roots`() {
        val body = certificates.joinToString(",", "[", "]") { "\"${pem(it).replace("\n", "\\n")}\"" }

        assertEquals(certificates.map { it.publicKey }, parseRoots(body))
    }

    @Test
    fun `refuses to trust an empty root list`() {
        assertFailsWith<IllegalStateException> { parseRoots("[]") }
    }

    @Test
    fun `reads the status list, ignoring the fields it has no use for`() {
        val revoked = parseRevoked(
            """
            {"entries": {
              "2c8cdddfd5e03bfc": {"status": "REVOKED", "expires": "2020-11-13",
                                   "reason": "KEY_COMPROMISE", "comment": "Key stored on unsecure system"},
              "c8966fcb2fbb0d7a": {"status": "SUSPENDED", "reason": "SOFTWARE_FLAW",
                                   "comment": "Bug in keystore causes this key malfunction b/555555"}
            }}
            """
        )

        assertEquals(2, revoked.size)
        assertEquals("REVOKED", revoked["2c8cdddfd5e03bfc"]?.status)
        assertEquals("SOFTWARE_FLAW", revoked["c8966fcb2fbb0d7a"]?.reason)
    }

    @Test
    fun `a list with no entries at all leaves nothing revoked`() {
        assertEquals(0, parseRevoked("""{"entries": {}}""").size)
    }

    @Test
    fun `finds a certificate by its serial in lower case hex`() = runBlocking {
        val listed = mapOf("c8966fcb2fbb0d7a" to Revocation("REVOKED", "KEY_COMPROMISE"))
        val lists = AttestationTrust.pinned(revoked = listed).current()

        val leaf = certificates.first()
        assertEquals(serial, leaf.serialNumber)
        assertEquals("KEY_COMPROMISE", lists.revocationOf(leaf)?.reason)
    }

    @Test
    fun `a serial nobody listed comes back clean`() = runBlocking {
        val listed = mapOf("2c8cdddfd5e03bfc" to Revocation("REVOKED"))
        val lists = AttestationTrust.pinned(revoked = listed).current()

        assertNull(lists.revocationOf(certificates.first()))
    }
}
