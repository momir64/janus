package rs.moma.janus.privezak.security

import javax.crypto.spec.GCMParameterSpec
import javax.crypto.Cipher.DECRYPT_MODE
import javax.crypto.Cipher.ENCRYPT_MODE
import java.security.SecureRandom
import javax.crypto.SecretKey
import javax.crypto.Cipher

internal const val TRANSFORMATION = "AES/GCM/NoPadding"
internal const val GCM_TAG_BITS = 128
internal const val KEY_BITS = 256
internal const val IV_BYTES = 12

private val random = SecureRandom()

internal fun randomBytes(size: Int) = ByteArray(size).also(random::nextBytes)

internal fun SecretKey.encrypt(plaintext: ByteArray, keystoreIv: Boolean = false): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    if (keystoreIv) cipher.init(ENCRYPT_MODE, this)
    else cipher.init(ENCRYPT_MODE, this, GCMParameterSpec(GCM_TAG_BITS, randomBytes(IV_BYTES)))
    return cipher.iv + cipher.doFinal(plaintext)
}

internal fun SecretKey.decrypt(stored: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(DECRYPT_MODE, this, GCMParameterSpec(GCM_TAG_BITS, stored.copyOf(IV_BYTES)))
    return cipher.doFinal(stored.copyOfRange(IV_BYTES, stored.size))
}
