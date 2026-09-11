package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.crypto.webauthn.isPrivezakAttestation
import rs.moma.janus.kredenac.crypto.webauthn.AttestationTrust
import rs.moma.janus.kredenac.crypto.webauthn.Revocation
import rs.moma.janus.kredenac.crypto.webauthn.CborValue
import rs.moma.janus.kredenac.utils.KeyAttestation
import rs.moma.janus.kredenac.utils.Authenticator
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.math.BigInteger
import kotlin.test.Test

class AndroidAttestationTest {
    private val serial = BigInteger("c8966fcb2fbb0d7a", 16)
    private val privezak = KeyAttestation(serial = serial)
    private val device = Authenticator()

    private val clientData = device.clientData("webauthn.create", "challenge")
    private val clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientData)

    private fun attested(describeIntermediate: Boolean = false): CborValue.Map {
        val chain = privezak.chain(device.publicKey, clientDataHash, describeIntermediate)
        return CborValue.from(device.androidKeyAttestationObject(chain, clientData))!!
    }

    private fun judge(attestation: CborValue.Map, trust: AttestationTrust): Boolean = runBlocking {
        isPrivezakAttestation(attestation, device.attestedCredentialData(), clientDataHash, device.publicKey, trust)
    }

    private fun trusting(revoked: Map<String, Revocation> = emptyMap()) =
        AttestationTrust.pinned(privezak.root, revoked = revoked)

    @Test
    fun `a chain reaching a published root is privezak`() {
        assertTrue(judge(attested(), trusting()))
    }

    @Test
    fun `nothing is privezak while the published roots are still unknown`() {
        assertFalse(judge(attested(), AttestationTrust.pinned()))
    }

    @Test
    fun `an attestation key google has revoked is not privezak`() {
        val listed = mapOf(serial.toString(16) to Revocation("REVOKED", "KEY_COMPROMISE"))
        assertFalse(judge(attested(), trusting(listed)))
    }

    @Test
    fun `a suspended key counts against the chain too`() {
        val listed = mapOf(serial.toString(16) to Revocation("SUSPENDED", "SOFTWARE_FLAW"))
        assertFalse(judge(attested(), trusting(listed)))
    }

    @Test
    fun `someone else's revoked serial leaves this chain alone`() {
        assertTrue(judge(attested(), trusting(mapOf("2c8cdddfd5e03bfc" to Revocation("REVOKED")))))
    }

    @Test
    fun `a chain described again below the first description is not privezak`() {
        assertFalse(judge(attested(describeIntermediate = true), trusting()))
    }
}
