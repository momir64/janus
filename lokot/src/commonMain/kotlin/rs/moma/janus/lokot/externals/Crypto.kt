package rs.moma.janus.lokot.externals

object Crypto {
    const val KEY_SIZE = 32
    const val NONCE_SIZE = 12
    const val TAG_SIZE = 16

    fun randomBytes(count: Int): ByteArray = platformRandomBytes(count)
    fun sha256(data: ByteArray): ByteArray = platformSha256(data)
    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = platformHmacSha256(key, data)

    fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        requireSizes(key, nonce)
        return platformAesGcmSeal(key, nonce, plaintext, aad)
    }

    fun aesGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray? {
        requireSizes(key, nonce)
        if (sealed.size < TAG_SIZE) return null
        return platformAesGcmOpen(key, nonce, sealed, aad)
    }

    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * 32)) { "HKDF length $length out of range" }

        val prk = hmacSha256(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val output = ByteArray(length)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1

        while (written < length) {
            previous = hmacSha256(prk, previous + info + byteArrayOf(counter.toByte()))
            val take = minOf(previous.size, length - written)
            previous.copyInto(output, written, 0, take)
            written += take
            counter++
        }

        prk.wipe()
        previous.wipe()
        return output
    }

    private fun requireSizes(key: ByteArray, nonce: ByteArray) {
        require(key.size == KEY_SIZE) { "key must be $KEY_SIZE bytes, was ${key.size}" }
        require(nonce.size == NONCE_SIZE) { "nonce must be $NONCE_SIZE bytes, was ${nonce.size}" }
    }
}

expect fun ByteArray.wipe()

fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

fun String.fromHex(): ByteArray {
    val cleaned = filterNot { it.isWhitespace() }
    require(cleaned.length % 2 == 0) { "hex string has odd length" }
    return ByteArray(cleaned.length / 2) { cleaned.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

internal expect fun platformRandomBytes(count: Int): ByteArray

internal expect fun platformSha256(data: ByteArray): ByteArray

internal expect fun platformHmacSha256(key: ByteArray, data: ByteArray): ByteArray

internal expect fun platformAesGcmSeal(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
    aad: ByteArray,
): ByteArray

internal expect fun platformAesGcmOpen(
    key: ByteArray,
    nonce: ByteArray,
    sealed: ByteArray,
    aad: ByteArray,
): ByteArray?
