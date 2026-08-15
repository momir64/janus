package rs.moma.janus.kredenac.crypto.authentication

import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import javax.crypto.Mac

class CsrfService(secret: ByteArray) {
    private val key = SecretKeySpec(secret, "HmacSHA256")

    fun tokenFor(sid: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return Base64.UrlSafe.withPadding(ABSENT).encode(mac.doFinal(sid.toByteArray()))
    }

    fun isValid(sid: String, providedToken: String?): Boolean {
        if (providedToken == null) return false
        val expected = tokenFor(sid)
        return MessageDigest.isEqual(expected.toByteArray(), providedToken.toByteArray())
    }
}
