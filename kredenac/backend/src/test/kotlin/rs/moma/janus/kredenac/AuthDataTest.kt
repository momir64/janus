package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.crypto.webauthn.parseAuthData
import rs.moma.janus.kredenac.common.BadRequestException
import java.security.spec.ECGenParameterSpec
import java.security.interfaces.ECPublicKey
import rs.moma.janus.kredenac.utils.Cbor
import kotlin.test.assertContentEquals
import java.security.KeyPairGenerator
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.math.BigInteger
import kotlin.test.Test
import kotlin.uuid.Uuid

class AuthDataTest {
    private val credentialId = ByteArray(32) { (it + 1).toByte() }
    private val aaguid = Uuid.parse("fcb1bcb4-f370-078c-6993-bc24d0ae3fbe")

    // A real P-256 key, so the COSE map holds a point the JDK will actually accept.
    private val point = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        (generateKeyPair().public as ECPublicKey).w
    }

    private fun coordinate(value: BigInteger): ByteArray {
        val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(32 - bytes.size) + bytes
    }

    private fun coseKey() = Cbor.map(
        Cbor.uint(1) to Cbor.uint(2),
        Cbor.uint(3) to Cbor.nint(-7),
        Cbor.nint(-1) to Cbor.uint(1),
        Cbor.nint(-2) to Cbor.bytes(coordinate(point.affineX)),
        Cbor.nint(-3) to Cbor.bytes(coordinate(point.affineY))
    )

    private fun authData(
        aaguidBytes: ByteArray = aaguid.toByteArray(),
        credential: ByteArray = credentialId,
        flags: Int = 0x45,
        key: ByteArray = coseKey()
    ): ByteArray = ByteArray(32) { 7 } +     // rpIdHash
            byteArrayOf(flags.toByte()) +    // flags: UP | UV | AT
            byteArrayOf(0, 0, 0, 1) +        // sign count
            aaguidBytes +
            byteArrayOf((credential.size shr 8).toByte(), credential.size.toByte()) +
            credential + key

    @Test
    fun `pulls the credential, the algorithm and the aaguid out of the attested data`() {
        val parsed = parseAuthData(authData())
        assertContentEquals(credentialId, parsed.credentialId)
        assertEquals("ES256", parsed.algorithm)
        assertEquals(aaguid, parsed.aaguid)
    }

    @Test
    fun `an all-zero aaguid means the authenticator declined to identify itself`() {
        assertNull(parseAuthData(authData(aaguidBytes = ByteArray(16))).aaguid)
    }

    @Test
    fun `finds the credential whatever its length, since the key follows it`() {
        for (length in listOf(16, 32, 64, 200)) {
            val credential = ByteArray(length) { (it % 127).toByte() }
            val parsed = parseAuthData(authData(credential = credential))

            assertContentEquals(credential, parsed.credentialId, "credential of $length bytes")
            assertEquals(aaguid, parsed.aaguid)
        }
    }

    @Test
    fun `refuses data without attested credentials`() {
        assertFailsWith<BadRequestException> { parseAuthData(authData(flags = 0x05)) }
    }

    @Test
    fun `refuses data that is too short to hold what it claims`() {
        assertFailsWith<BadRequestException> { parseAuthData(ByteArray(36)) }
        assertFailsWith<BadRequestException> { parseAuthData(authData().copyOfRange(0, 54)) }

        val truncated = authData().copyOfRange(0, 70)
        assertFailsWith<BadRequestException> { parseAuthData(truncated) }
    }

    @Test
    fun `refuses a cose key it cannot use`() {
        val noAlgorithm = Cbor.map(Cbor.uint(1) to Cbor.uint(2))
        assertFailsWith<BadRequestException> { parseAuthData(authData(key = noAlgorithm)) }

        val unknownKeyType = Cbor.map(Cbor.uint(1) to Cbor.uint(9), Cbor.uint(3) to Cbor.nint(-7))
        assertFailsWith<BadRequestException> { parseAuthData(authData(key = unknownKeyType)) }
    }
}
