package rs.moma.janus.lokot.externals

import kotlinx.cinterop.*
import crypto.*

@OptIn(ExperimentalForeignApi::class)
private object Native {
    const val NONCE_SIZE = Crypto.NONCE_SIZE
    const val TAG_SIZE = Crypto.TAG_SIZE

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

    /** AES-256-GCM. Returns ciphertext with the 16-byte tag appended. */
    fun aesGcmSeal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray, aad: ByteArray): ByteArray {
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

    private fun check(result: Int, call: String) {
        if (result != 1) error("$call failed")
    }
}

internal actual fun platformRandomBytes(count: Int): ByteArray = Native.randomBytes(count)

internal actual fun platformSha256(data: ByteArray): ByteArray = Native.sha256(data)

internal actual fun platformHmacSha256(key: ByteArray, data: ByteArray): ByteArray = Native.hmacSha256(key, data)

internal actual fun platformAesGcmSeal(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
    aad: ByteArray,
): ByteArray = Native.aesGcmSeal(key, nonce, plaintext, aad)

internal actual fun platformAesGcmOpen(
    key: ByteArray,
    nonce: ByteArray,
    sealed: ByteArray,
    aad: ByteArray,
): ByteArray? = Native.aesGcmOpen(key, nonce, sealed, aad)

fun ByteArray.size(): ULong = size.toULong()

@OptIn(ExperimentalForeignApi::class)
actual fun ByteArray.wipe() {
    if (isEmpty()) return
    usePinned { OPENSSL_cleanse(it.addressOf(0), size()) }
}

internal actual fun ByteArray.toChars(): CharArray = decodeToString().toCharArray()

@OptIn(ExperimentalForeignApi::class)
internal fun ByteArray.toUBytes(scope: MemScope): CPointer<UByteVar> {
    val buffer = scope.allocArray<UByteVar>(maxOf(size, 1))
    forEachIndexed { index, byte -> buffer[index] = byte.toUByte() }
    return buffer
}
