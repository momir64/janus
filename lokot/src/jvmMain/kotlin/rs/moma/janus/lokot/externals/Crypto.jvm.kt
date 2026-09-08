package rs.moma.janus.lokot.externals

import javax.crypto.spec.GCMParameterSpec
import javax.crypto.AEADBadTagException
import javax.crypto.spec.SecretKeySpec
import java.security.MessageDigest
import java.security.SecureRandom
import java.nio.ByteBuffer
import javax.crypto.Cipher
import java.util.Arrays
import javax.crypto.Mac

private val random = SecureRandom()

internal actual fun platformRandomBytes(count: Int): ByteArray = ByteArray(count).also { random.nextBytes(it) }

internal actual fun platformSha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

internal actual fun platformHmacSha256(key: ByteArray, data: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)

internal actual fun platformAesGcmSeal(
    key: ByteArray,
    nonce: ByteArray,
    plaintext: ByteArray,
    aad: ByteArray,
): ByteArray = cipher(Cipher.ENCRYPT_MODE, key, nonce, aad).doFinal(plaintext)

internal actual fun platformAesGcmOpen(
    key: ByteArray,
    nonce: ByteArray,
    sealed: ByteArray,
    aad: ByteArray,
): ByteArray? = try {
    cipher(Cipher.DECRYPT_MODE, key, nonce, aad).doFinal(sealed)
} catch (_: AEADBadTagException) {
    null // the tag did not verify, either from tampering or a wrong key
}

// The tag is appended to the ciphertext, JCA does by itself
private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray, aad: ByteArray): Cipher =
    Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(Crypto.TAG_SIZE * 8, nonce))
        if (aad.isNotEmpty()) updateAAD(aad)
    }

actual fun ByteArray.wipe() = Arrays.fill(this, 0)

internal actual fun ByteArray.toChars(): CharArray {
    val decoded = Charsets.UTF_8.decode(ByteBuffer.wrap(this))
    return CharArray(decoded.remaining()).also { decoded.get(it) }
}
