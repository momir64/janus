package rs.moma.janus.kredenac.crypto.algorithms

import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.math.BigInteger
import java.security.spec.*

enum class EcCurve(val curveName: String) {
    P256("secp256r1"), P384("secp384r1"), P521("secp521r1")
}

object EcUtil {
    fun toPublicKey(curve: EcCurve, x: ByteArray, y: ByteArray): ByteArray {
        val parameters = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec(curve.curveName)) }
        val params = parameters.getParameterSpec(ECParameterSpec::class.java)
        val point = ECPoint(BigInteger(1, x), BigInteger(1, y))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(point, params)).encoded
    }
}

object RsaUtil {
    fun toPublicKey(modulus: ByteArray, exponent: ByteArray): ByteArray {
        val modulus = BigInteger(1, modulus)
        val exponent = BigInteger(1, exponent)
        val keySpec = RSAPublicKeySpec(modulus, exponent)
        return KeyFactory.getInstance("RSA").generatePublic(keySpec).encoded
    }
}

object EdUtil {
    private val ED25519_X509_PREFIX = byteArrayOf(0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x03, 0x21, 0x00)

    fun toPublicKey(key: ByteArray): ByteArray {
        require(key.size == 32) { "Ed25519 public key must be 32 bytes" }
        val keySpec = X509EncodedKeySpec(ED25519_X509_PREFIX + key)
        return KeyFactory.getInstance("Ed25519").generatePublic(keySpec).encoded
    }
}