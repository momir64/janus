package rs.moma.janus.kredenac.crypto.webauthn

import rs.moma.janus.kredenac.crypto.webauthn.CborValue.Companion.get
import rs.moma.janus.kredenac.crypto.algorithms.VerifyUtil
import java.security.cert.CertificateFactory
import java.security.spec.X509EncodedKeySpec
import java.security.cert.X509Certificate
import kotlin.io.encoding.Base64
import java.security.KeyFactory
import java.security.PublicKey

private const val GOOGLE_ATTESTATION_ROOT = """
    MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xU
    FmOr75gvMsd/dTEDDJdSSxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5j
    lRfdnJLmN0pTy/4lj4/7tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y
    //0rb+T+W8a9nsNL/ggjnar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73X
    pXyTqRxB/M0n1n/W9nGqC4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYI
    mQQcHtGl/m00QLVWutHQoVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB
    +TxywElgS70vE0XmLD+OJtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7q
    uvmag8jfPioyKvxnK/EgsTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgp
    Zrt3i5MIlCaY504LzSRiigHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7
    gLiMm0jhO2B6tUXHI/+MRPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82
    ixPvZtXQpUpuL12ab+9EaDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+
    NpUFgNPN9PvQi8WEg5UmAGMCAwEAAQ==
"""

private const val PRIVEZAK_PACKAGE = "rs.moma.janus.privezak"

internal val PRIVEZAK_SIGNERS = setOf(
    "879473bbf61bbf6dd29eb5563ff64f48ded861283287144eb9768cdd1df06572", // release
//    "054ff4645233b6d8d86403649f13f0f606844db812a03cefb11dc25ab706bb39", // debug
)

private const val KEY_DESCRIPTION_OID = "1.3.6.1.4.1.11129.2.1.17"
private const val ATTESTATION_APPLICATION_ID_TAG = 709L
private const val ATTESTATION_CHALLENGE_INDEX = 4
private const val SOFTWARE_ENFORCED_INDEX = 6

fun googleAttestationRoot(): PublicKey = KeyFactory.getInstance("RSA")
    .generatePublic(X509EncodedKeySpec(Base64.Mime.decode(GOOGLE_ATTESTATION_ROOT)))

internal fun isPrivezakAttestation(
    attestation: CborValue.Map, authData: ByteArray, clientDataHash: ByteArray,
    credentialPublicKey: ByteArray, root: PublicKey
): Boolean = runCatching {
    if (attestation["fmt"]?.let { (it as? CborValue.TextStr)?.value } != "android-key") return false
    val attStmt = attestation["attStmt"] as? CborValue.Map ?: return false
    val chain = (attStmt["x5c"] as? CborValue.Arr)?.value?.mapNotNull { it.asByteStr() } ?: return false
    if (chain.isEmpty()) return false

    val certificates = chain.map { it.toCertificate() }
    val leaf = certificates.first()

    certificates.zipWithNext().forEach { (certificate, issuer) -> certificate.verify(issuer.publicKey) }
    if (!certificates.last().publicKey.encoded.contentEquals(root.encoded)) return false
    certificates.last().verify(root)
    if (!leaf.publicKey.encoded.contentEquals(credentialPublicKey)) return false

    val algorithm = VerifyUtil(attStmt["alg"]?.asInteger() ?: return false)
    val signature = attStmt["sig"]?.asByteStr() ?: return false
    if (!algorithm.verify(leaf.publicKey.encoded, authData + clientDataHash, signature)) return false

    val description = leaf.getExtensionValue(KEY_DESCRIPTION_OID)?.let { Der(it).read().content }
    val fields = Der(description ?: return false).read().children()

    // Rejects a chain minted for a different registration.
    if (!fields[ATTESTATION_CHALLENGE_INDEX].content.contentEquals(clientDataHash)) return false

    val applicationId = fields[SOFTWARE_ENFORCED_INDEX].children()
        .find { it.tag == ATTESTATION_APPLICATION_ID_TAG }
        ?.let { Der(it.content).read().content }
        ?.let { Der(it).read().children() } ?: return false

    val packages = applicationId[0].children().map { it.children()[0].content.decodeToString() }
    val signers = applicationId[1].children().map { it.content.toHex() }

    packages.contains(PRIVEZAK_PACKAGE) && signers.any { it in PRIVEZAK_SIGNERS }
}.getOrDefault(false)

private fun ByteArray.toCertificate(): X509Certificate =
    CertificateFactory.getInstance("X.509").generateCertificate(inputStream()) as X509Certificate

private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

private class DerValue(val tag: Long, val content: ByteArray) {
    fun children(): List<DerValue> = Der(content).let { der ->
        buildList { while (der.hasMore()) add(der.read()) }
    }
}

private class Der(private val bytes: ByteArray, private var position: Int = 0) {
    fun hasMore() = position < bytes.size

    fun read(): DerValue {
        var tag = (byte() and 0x1F).toLong()
        if (tag == 0x1FL) {
            tag = 0
            do {
                val next = byte()
                tag = (tag shl 7) or (next and 0x7F).toLong()
            } while (next and 0x80 != 0)
        }

        var length = byte()
        if (length and 0x80 != 0) {
            var value = 0
            repeat(length and 0x7F) {
                value = (value shl 8) or byte()
            }
            length = value
        }

        if (position + length > bytes.size) throw IllegalArgumentException("Truncated DER value")
        val content = bytes.copyOfRange(position, position + length)
        position += length

        return DerValue(tag, content)
    }

    private fun byte(): Int {
        if (position >= bytes.size) throw IllegalArgumentException("Unexpected end of DER input")
        return bytes[position++].toInt() and 0xFF
    }
}
