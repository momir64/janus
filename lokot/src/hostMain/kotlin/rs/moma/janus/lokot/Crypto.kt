package rs.moma.janus.lokot

import kotlinx.cinterop.*
import crypto.*

@OptIn(ExperimentalForeignApi::class)
object Crypto {
    const val KEY_SIZE = 32
    const val NONCE_SIZE = 12
    const val TAG_SIZE = 16

    context(scope: MemScope)
    val ByteArray.uBytes: CPointer<UByteVar>; get() = this.toUBytes(scope)

    fun randomBytes(count: Int): ByteArray = memScoped {
        val buffer = allocArray<UByteVar>(count)
        if (RAND_bytes(buffer, count) != 1) error("RAND_bytes failed")
        buffer.readBytes(count)
    }

    fun sha256(data: ByteArray): ByteArray = memScoped {
        val digest = allocArray<UByteVar>(32)
        SHA256(data.uBytes, data.size(), digest)
        digest.readBytes(32)
    }

    fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray = memScoped {
        val digest = allocArray<UByteVar>(32)
        val length = alloc<UIntVar>()
        HMAC(EVP_sha256(), key.uBytes, key.size, data.uBytes, data.size(), digest, length.ptr) ?: error("HMAC failed")
        if (length.value.toInt() != 32) error("HMAC returned ${length.value} bytes, expected 32")
        digest.readBytes(32)
    }

    /**
     * RFC 5869 HKDF. Extract folds [ikm] and [salt] into a pseudorandom key,
     * expand stretches that to [length] bytes bound to [info].
     */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..(255 * 32)) { "HKDF length $length out of range" }

        // 2.2. Step 1: Extract - "If not provided, the salt is set to a string of HashLen zeros"
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

    /** AES-256-GCM. Returns ciphertext with the 16-byte tag appended. */
    fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
        requireSizes(key, nonce)
        return memScoped {
            withCipherContext { ctx ->
                val written = alloc<IntVar>()
                val output = allocArray<UByteVar>(plaintext.size + TAG_SIZE)

                check(EVP_EncryptInit_ex(ctx, EVP_aes_256_gcm(), null, null, null), "EncryptInit")
                check(EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, NONCE_SIZE, null), "SET_IVLEN")
                check(EVP_EncryptInit_ex(ctx, null, null, key.uBytes, nonce.uBytes), "EncryptInit key")
                if (aad.isNotEmpty())
                    check(EVP_EncryptUpdate(ctx, null, written.ptr, aad.uBytes, aad.size), "EncryptUpdate aad")

                check(EVP_EncryptUpdate(ctx, output, written.ptr, plaintext.uBytes, plaintext.size), "EncryptUpdate")
                val bodyLength = written.value
                check(EVP_EncryptFinal_ex(ctx, output + bodyLength, written.ptr), "EncryptFinal")
                val totalLength = bodyLength + written.value

                check(EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_GET_TAG, TAG_SIZE, output + totalLength), "GET_TAG")
                output.readBytes(totalLength + TAG_SIZE)
            }
        }
    }

    /** Reverses [aesGcmSeal]. Returns null when the tag does not verify, either because of tampering or a wrong key. */
    fun aesGcmOpen(key: ByteArray, nonce: ByteArray, sealed: ByteArray, aad: ByteArray): ByteArray? {
        requireSizes(key, nonce)
        if (sealed.size < TAG_SIZE) return null

        val bodyLength = sealed.size - TAG_SIZE
        return memScoped {
            withCipherContext { ctx ->
                val output = allocArray<UByteVar>(maxOf(bodyLength, 1))
                val tag = sealed.copyOfRange(bodyLength, sealed.size)
                val body = sealed.copyOfRange(0, bodyLength)
                val written = alloc<IntVar>()

                check(EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), null, null, null), "DecryptInit")
                check(EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, NONCE_SIZE, null), "SET_IVLEN")
                check(EVP_DecryptInit_ex(ctx, null, null, key.uBytes, nonce.uBytes), "DecryptInit key")

                if (aad.isNotEmpty())
                    check(EVP_DecryptUpdate(ctx, null, written.ptr, aad.uBytes, aad.size), "DecryptUpdate aad")

                check(EVP_DecryptUpdate(ctx, output, written.ptr, body.uBytes, bodyLength), "DecryptUpdate")
                val plainLength = written.value

                check(EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, TAG_SIZE, tag.uBytes), "SET_TAG")

                if (EVP_DecryptFinal_ex(ctx, output + plainLength, written.ptr) <= 0) null
                else output.readBytes(plainLength + written.value)
            }
        }
    }

    private inline fun <T> withCipherContext(block: (CPointer<EVP_CIPHER_CTX>) -> T): T {
        val context = EVP_CIPHER_CTX_new() ?: error("EVP_CIPHER_CTX_new failed")
        try {
            return block(context)
        } finally {
            EVP_CIPHER_CTX_free(context)
        }
    }

    private fun requireSizes(key: ByteArray, nonce: ByteArray) {
        require(key.size == KEY_SIZE) { "key must be $KEY_SIZE bytes, was ${key.size}" }
        require(nonce.size == NONCE_SIZE) { "nonce must be $NONCE_SIZE bytes, was ${nonce.size}" }
    }

    private fun check(result: Int, call: String) {
        if (result != 1) error("$call failed")
    }
}

fun ByteArray.size(): ULong = size.toULong()

@OptIn(ExperimentalForeignApi::class)
fun ByteArray.wipe() {
    if (isEmpty()) return
    usePinned { OPENSSL_cleanse(it.addressOf(0), size()) }
}

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toUBytes(scope: MemScope): CPointer<UByteVar> {
    val buffer = scope.allocArray<UByteVar>(maxOf(size, 1))
    forEachIndexed { index, byte -> buffer[index] = byte.toUByte() }
    return buffer
}

fun ByteArray.toHex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

fun String.fromHex(): ByteArray {
    val cleaned = filterNot { it.isWhitespace() }
    require(cleaned.length % 2 == 0) { "hex string has odd length" }
    return ByteArray(cleaned.length / 2) { cleaned.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
