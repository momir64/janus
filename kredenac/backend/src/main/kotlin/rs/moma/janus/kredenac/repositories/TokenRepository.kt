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
    private val tokenEncryptionKey: ByteArray,
    private val hmacSecret: ByteArray
) {
    private fun keyOf(token: String) = HmacUtil.hash(hmacSecret, token)

    suspend fun insert(token: String, duration: Duration, bond: String? = null, bondIsToken: Boolean = false) {
        val value = bond?.let { if (bondIsToken) keyOf(it) else it }
        val key = keyOf(token)
        val stored = value?.let {
            val encrypted = AesUtil.encrypt(tokenEncryptionKey, it.toByteArray(), key.toByteArray())
            Base64.encode(encrypted.iv) + ":" + Base64.encode(encrypted.ciphertext)
        } ?: ""
        redis.setex(key, duration.inWholeSeconds, stored)
    }

    suspend fun consumePresence(token: String): Boolean = redis.getdel(keyOf(token)) != null

    suspend fun consumeBond(challenge: String): String {
        val key = keyOf(challenge)
        val intermediate = decrypt(key, redis.getdel(key))
        return decrypt(intermediate, redis.getdel(intermediate))
    }

    suspend fun peek(token: String): String {
        val key = keyOf(token)
        return decrypt(key, redis.get(key))
    }

    private fun decrypt(key: String, value: String?): String {
        value ?: throw BadRequestException("Token isn't pending, it might have expired")
        val parts = value.split(":")
        if (parts.size != 2) throw BadRequestException("Invalid email token")
        val (iv, ciphertext) = parts.map { Base64.decode(it) }
        return try {
            String(AesUtil.decrypt(tokenEncryptionKey, ciphertext, iv, key.toByteArray()))
        } catch (_: AEADBadTagException) {
            throw CompromisedException("Token email failed integrity check")
        }
    }
}
