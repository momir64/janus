package rs.moma.janus.kredenac.crypto.algorithms

import rs.moma.janus.kredenac.utils.BadRequestException
import java.security.spec.MGF1ParameterSpec.SHA256
import java.security.spec.X509EncodedKeySpec
import java.security.spec.PSSParameterSpec
import java.security.KeyFactory
import java.security.Signature

enum class VerifyUtil(
    val coseCode: Long,
    val algorithm: String,
    private val keyFactoryAlgorithm: String,
    private val signatureAlgorithm: String
) {
    ES256(-7, "ES256", "EC", "SHA256withECDSA"),
    ESP256(-9, "ESP256", "EC", "SHA256withECDSA"),
    ES384(-35, "ES384", "EC", "SHA384withECDSA"),
    ESP384(-51, "ESP384", "EC", "SHA384withECDSA"),
    ES512(-36, "ES512", "EC", "SHA512withECDSA"),
    ESP512(-52, "ESP512", "EC", "SHA512withECDSA"),
    RS256(-257, "RS256", "RSA", "SHA256withRSA"),
    PS256(-37, "PS256", "RSA", "RSASSA-PSS"),
    ED25519(-8, "EdDSA", "Ed25519", "Ed25519");


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

        operator fun invoke(algorithm: String): VerifyUtil =
            entries.find { it.algorithm == algorithm } ?: throw BadRequestException("Unsupported algorithm: $algorithm")
    }
}
