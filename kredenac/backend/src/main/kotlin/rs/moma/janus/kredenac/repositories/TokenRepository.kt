package rs.moma.janus.kredenac.repositories

import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import rs.moma.janus.kredenac.common.CompromisedException
import rs.moma.janus.kredenac.common.BadRequestException
import rs.moma.janus.kredenac.crypto.algorithms.HmacUtil
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import rs.moma.janus.kredenac.crypto.algorithms.AesUtil
import javax.crypto.AEADBadTagException
import kotlin.io.encoding.Base64
import kotlin.time.Duration

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class TokenRepository(
    private val redis: RedisCoroutinesCommands<String, String>,
    private val emailEncryptionKey: ByteArray,
    private val hmacSecret: ByteArray
) {
    suspend fun insert(token: String, duration: Duration, email: String? = null) {
        val tokenHash = HmacUtil.hash(hmacSecret, token)
        val value = when (email) {
            null -> ""
            else -> {
                val encrypted = AesUtil.encrypt(emailEncryptionKey, email.toByteArray(), tokenHash.toByteArray())
                Base64.encode(encrypted.iv) + ":" + Base64.encode(encrypted.ciphertext)
            }
        }
        redis.setex(tokenHash, duration.inWholeSeconds, value)
    }

    suspend fun consumePresence(token: String): Boolean {
        return redis.getdel(HmacUtil.hash(hmacSecret, token)) != null
    }

    suspend fun consume(token: String): String {
        val tokenHash = HmacUtil.hash(hmacSecret, token)
        val value = redis.getdel(tokenHash) ?: throw BadRequestException("Token isn't pending, it might have expired")
        val parts = value.split(":")
        if (parts.size != 2) throw BadRequestException("Invalid email token")
        val (iv, ciphertext) = parts.map { Base64.decode(it) }
        return try {
            String(AesUtil.decrypt(emailEncryptionKey, ciphertext, iv, tokenHash.toByteArray()))
        } catch (_: AEADBadTagException) {
            throw CompromisedException("Token email failed integrity check")
        }
    }
}