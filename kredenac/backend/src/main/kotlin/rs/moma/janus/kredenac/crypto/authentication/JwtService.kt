package rs.moma.janus.kredenac.crypto.authentication

import kotlin.io.encoding.Base64.PaddingOption.ABSENT_OPTIONAL
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Clock
import javax.crypto.Mac
import kotlin.uuid.Uuid

@Serializable
data class JwtClaims(val sub: String, val sid: String, val cid: String, val iat: Long, val exp: Long)

class JwtService(secret: ByteArray, val accessTokenTtl: Duration = 5.minutes) {
    private val base64 = Base64.UrlSafe.withPadding(ABSENT_OPTIONAL)
    private val key = SecretKeySpec(secret, "HmacSHA256")
    private val json = Json { ignoreUnknownKeys = true }

    fun issue(userId: Uuid, sid: String, cid: Uuid, ttl: Duration = accessTokenTtl): String {
        val now = Clock.System.now()
        val claims = JwtClaims(userId.toString(), sid, cid.toString(), now.epochSeconds, (now + ttl).epochSeconds)
        val headerEncoded = base64.encode("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
        val payloadEncoded = base64.encode(json.encodeToString(claims).toByteArray())
        val signingInput = "$headerEncoded.$payloadEncoded"
        val signatureEncoded = base64.encode(sign(signingInput))
        return "$signingInput.$signatureEncoded"
    }

    fun verify(token: String): JwtClaims? {
        val parts = token.split(".")
        if (parts.size != 3) return null
        val (headerEncoded, payloadEncoded, signatureEncoded) = parts
        val signingInput = "$headerEncoded.$payloadEncoded"

        val actualSignature = try {
            base64.decode(signatureEncoded)
        } catch (_: IllegalArgumentException) {
            return null
        }

        if (!MessageDigest.isEqual(sign(signingInput), actualSignature))
            return null

        val claims = try {
            json.decodeFromString<JwtClaims>(String(base64.decode(payloadEncoded)))
        } catch (_: Exception) {
            return null
        }

        if (claims.exp < Clock.System.now().epochSeconds) return null
        return claims
    }

    private fun sign(input: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(key)
        return mac.doFinal(input.toByteArray())
    }
}
