package rs.moma.janus.kredenac.crypto.algorithms

import rs.moma.janus.kredenac.crypto.algorithms.EcCurve.*
import rs.moma.janus.kredenac.common.BadRequestException
import java.security.spec.MGF1ParameterSpec.SHA256
import java.security.spec.X509EncodedKeySpec
import java.security.spec.PSSParameterSpec
import java.security.KeyFactory
import java.security.Signature

enum class VerifyUtil(
    val coseCode: Long,
    private val keyFactoryAlgorithm: String,
    private val signatureAlgorithm: String,
    val curve: EcCurve? = null
) {
    ES256(-7, "EC", "SHA256withECDSA", P256),
    ESP256(-9, "EC", "SHA256withECDSA", P256),
    ES384(-35, "EC", "SHA384withECDSA", P384),
    ESP384(-51, "EC", "SHA384withECDSA", P384),
    ES512(-36, "EC", "SHA512withECDSA", P521),
    ESP512(-52, "EC", "SHA512withECDSA", P521),
    RS256(-257, "RSA", "SHA256withRSA"),
    PS256(-37, "RSA", "RSASSA-PSS"),
    EdDSA(-8, "Ed25519", "Ed25519"),
    Ed25519(-19, "Ed25519", "Ed25519");

    fun verify(publicKeyBytes: ByteArray, signedData: ByteArray, signatureBytes: ByteArray): Boolean {
        val keyFactory = KeyFactory.getInstance(keyFactoryAlgorithm)
        val signature = Signature.getInstance(signatureAlgorithm)
        val params = PSSParameterSpec("SHA-256", "MGF1", SHA256, 32, 1)
        val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
        if (this == PS256) signature.setParameter(params)
        signature.initVerify(publicKey)
        signature.update(signedData)
        return signature.verify(signatureBytes)
    }

    companion object {
        operator fun invoke(coseCode: Long): VerifyUtil =
            entries.find { it.coseCode == coseCode } ?: throw BadRequestException("Unsupported algorithm: $coseCode")

        operator fun invoke(name: String): VerifyUtil =
            entries.find { it.name == name } ?: throw BadRequestException("Unsupported algorithm: $name")
    }
}
