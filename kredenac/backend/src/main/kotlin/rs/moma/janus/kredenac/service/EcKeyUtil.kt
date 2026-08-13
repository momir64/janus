package rs.moma.janus.kredenac.service

import java.security.spec.ECGenParameterSpec
import java.security.interfaces.ECPublicKey
import java.security.spec.ECParameterSpec
import java.security.spec.ECPublicKeySpec
import java.security.AlgorithmParameters
import java.security.spec.ECPoint
import java.security.KeyFactory
import java.math.BigInteger

object EcKeyUtil {
    private val p256Params: ECParameterSpec by lazy {
        val parameters = AlgorithmParameters.getInstance("EC")
        parameters.init(ECGenParameterSpec("secp256r1"))
        parameters.getParameterSpec(ECParameterSpec::class.java)
    }

    fun reconstructPublicKey(x: ByteArray, y: ByteArray): ECPublicKey {
        val point = ECPoint(BigInteger(1, x), BigInteger(1, y))
        val keySpec = ECPublicKeySpec(point, p256Params)
        val keyFactory = KeyFactory.getInstance("EC")
        return keyFactory.generatePublic(keySpec) as ECPublicKey
    }
}
