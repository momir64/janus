package rs.moma.janus.kredenac.crypto.algorithms

import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac

object HmacUtil {
    fun hash(secret: ByteArray, value: String): String = hash(secret, value.toByteArray())

    fun hash(secret: ByteArray, value: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret, "HmacSHA256"))
        return mac.doFinal(value).joinToString("") { "%02x".format(it) }
    }
}
