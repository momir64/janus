package rs.moma.janus.kredenac.utils

import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import java.security.spec.ECGenParameterSpec
import java.security.interfaces.ECPublicKey
import java.security.KeyPairGenerator
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import java.security.Signature
import java.security.KeyPair
import java.math.BigInteger
import kotlin.uuid.Uuid

// A P-256 authenticator in software: it holds a key, reports a sign count, and signs
// assertions the way a real one does, so the ceremonies can be driven without hardware.
class Authenticator(
    private val rpId: String = "kredenac.moma.rs",
    private val origin: String = "https://kredenac.moma.rs",
    val aaguid: Uuid? = Uuid.parse("fcb1bcb4-f370-078c-6993-bc24d0ae3fbe"),
    val credentialId: ByteArray = ByteArray(32) { (it + 1).toByte() }
) {
    private val base64 = Base64.UrlSafe.withPadding(ABSENT)
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    var signCount: Long = 0
    val publicKey: ByteArray get() = keyPair.public.encoded

    private fun rpIdHash() = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray())

    fun clientData(type: String, challenge: String, origin: String = this.origin): ByteArray =
        """{"type":"$type","challenge":"$challenge","origin":"$origin"}""".toByteArray()

    // rpIdHash | flags (user present, user verified) | sign count
    fun authenticatorData(count: Long = signCount, flags: Int = 0x05): ByteArray =
        rpIdHash() + byteArrayOf(flags.toByte()) +
                byteArrayOf((count shr 24).toByte(), (count shr 16).toByte(), (count shr 8).toByte(), count.toByte())

    fun sign(authenticatorData: ByteArray, clientDataJSON: ByteArray): ByteArray {
        val clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataJSON)
        return Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(authenticatorData + clientDataHash)
            sign()
        }
    }

    fun encode(bytes: ByteArray): String = base64.encode(bytes)

    // What an authenticator sends when it attests to nothing at all.
    fun attestationObject(): ByteArray = attestationObject("none", Cbor.map())

    fun androidKeyAttestationObject(chain: List<ByteArray>, clientDataJSON: ByteArray): ByteArray =
        attestationObject(
            "android-key", Cbor.map(
                Cbor.text("alg") to Cbor.nint(-7),
                Cbor.text("sig") to Cbor.bytes(sign(attestedCredentialData(), clientDataJSON)),
                Cbor.text("x5c") to Cbor.array(*chain.map { Cbor.bytes(it) }.toTypedArray())
            )
        )

    private fun attestationObject(format: String, attStmt: ByteArray): ByteArray = Cbor.map(
        Cbor.text("fmt") to Cbor.text(format),
        Cbor.text("attStmt") to attStmt,
        Cbor.text("authData") to Cbor.bytes(attestedCredentialData())
    )

    // The attested credential data a registration returns.
    fun attestedCredentialData(): ByteArray {
        val point = (keyPair.public as ECPublicKey).w
        val coseKey = Cbor.map(
            Cbor.uint(1) to Cbor.uint(2),
            Cbor.uint(3) to Cbor.nint(-7),
            Cbor.nint(-1) to Cbor.uint(1),
            Cbor.nint(-2) to Cbor.bytes(coordinate(point.affineX)),
            Cbor.nint(-3) to Cbor.bytes(coordinate(point.affineY))
        )

        return authenticatorData(flags = 0x45) +
                (aaguid?.toByteArray() ?: ByteArray(16)) +
                byteArrayOf((credentialId.size shr 8).toByte(), credentialId.size.toByte()) +
                credentialId +
                coseKey
    }

    private fun coordinate(value: BigInteger): ByteArray {
        val bytes = value.toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
        return ByteArray(32 - bytes.size) + bytes
    }
}
