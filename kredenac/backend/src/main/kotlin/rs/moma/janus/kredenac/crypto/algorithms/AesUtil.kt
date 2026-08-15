package rs.moma.janus.kredenac.crypto.algorithms

import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import javax.crypto.Cipher

object AesUtil {
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private val secureRandom = SecureRandom()

    class Encrypted(val ciphertext: ByteArray, val iv: ByteArray)

    fun generateKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return key
    }

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): Encrypted {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        return Encrypted(cipher.doFinal(plaintext), iv)
    }

    fun decrypt(key: ByteArray, ciphertext: ByteArray, iv: ByteArray, aad: ByteArray? = null): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        return cipher.doFinal(ciphertext)
    }
}
