package rs.moma.janus.kredenac.crypto.webauthn

import rs.moma.janus.kredenac.crypto.webauthn.CborValue.Companion.get
import rs.moma.janus.kredenac.crypto.algorithms.VerifyUtil
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

private const val PRIVEZAK_PACKAGE = "rs.moma.janus.privezak"

internal val PRIVEZAK_SIGNERS = setOf(
    "879473bbf61bbf6dd29eb5563ff64f48ded861283287144eb9768cdd1df06572", // release
//    "054ff4645233b6d8d86403649f13f0f606844db812a03cefb11dc25ab706bb39", // debug
)

private const val KEY_DESCRIPTION_OID = "1.3.6.1.4.1.11129.2.1.17"
private const val ATTESTATION_APPLICATION_ID_TAG = 709L
private const val ATTESTATION_CHALLENGE_INDEX = 4
private const val SOFTWARE_ENFORCED_INDEX = 6

internal suspend fun isPrivezakAttestation(
    attestation: CborValue.Map, authData: ByteArray, clientDataHash: ByteArray,
    credentialPublicKey: ByteArray, trust: AttestationTrust
): Boolean = runCatching {
    if (attestation["fmt"]?.let { (it as? CborValue.TextStr)?.value } != "android-key") return false
    val attStmt = attestation["attStmt"] as? CborValue.Map ?: return false
    val chain = (attStmt["x5c"] as? CborValue.Arr)?.value?.mapNotNull { it.asByteStr() } ?: return false
    if (chain.isEmpty()) return false

    val certificates = chain.map { it.toCertificate() }
    val leaf = certificates.first()
    val lists = trust.current()

    certificates.zipWithNext().forEach { (certificate, issuer) -> certificate.verify(issuer.publicKey) }
    val root = lists.roots.find { it.encoded.contentEquals(certificates.last().publicKey.encoded) } ?: return false
    certificates.last().verify(root)

    if (certificates.any { lists.revocationOf(it) != null }) return false
    if (!leaf.publicKey.encoded.contentEquals(credentialPublicKey)) return false

    val algorithm = VerifyUtil(attStmt["alg"]?.asInteger() ?: return false)
    val signature = attStmt["sig"]?.asByteStr() ?: return false
    if (!algorithm.verify(leaf.publicKey.encoded, authData + clientDataHash, signature)) return false

    if (certificates.indexOfLast { it.getExtensionValue(KEY_DESCRIPTION_OID) != null } != 0) return false
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
