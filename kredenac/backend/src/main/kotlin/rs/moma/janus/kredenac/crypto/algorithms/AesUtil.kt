package rs.moma.janus.kredenac.crypto.algorithms

import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.CipherInputStream
import java.security.SecureRandom
import java.io.InputStream
import javax.crypto.Cipher

object AesUtil {
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12
    private val secureRandom = SecureRandom()
    const val GCM_TAG_LENGTH_BYTES = 16

    class Encrypted(val ciphertext: ByteArray, val iv: ByteArray)
    class EncryptedStream(val stream: CipherInputStream, val iv: ByteArray)

    fun generateKey(): ByteArray {
        val key = ByteArray(32)
        secureRandom.nextBytes(key)
        return key
    }

    private fun getEncryptCipher(key: ByteArray, aad: ByteArray? = null): Pair<Cipher, ByteArray> {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        secureRandom.nextBytes(iv)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        return cipher to iv
    }

    private fun getDecryptCipher(key: ByteArray, iv: ByteArray, aad: ByteArray? = null): Cipher {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        aad?.let { cipher.updateAAD(it) }
        return cipher
    }

    fun encrypt(key: ByteArray, plaintext: ByteArray, aad: ByteArray? = null): Encrypted {
        val (cipher, iv) = getEncryptCipher(key, aad)
        return Encrypted(cipher.doFinal(plaintext), iv)
    }

    fun encrypt(key: ByteArray, plaintext: InputStream, aad: ByteArray? = null): EncryptedStream {
        val (cipher, iv) = getEncryptCipher(key, aad)
        return EncryptedStream(CipherInputStream(plaintext, cipher), iv)
    }

    fun decrypt(key: ByteArray, ciphertext: ByteArray, iv: ByteArray, aad: ByteArray? = null): ByteArray {
        return getDecryptCipher(key, iv, aad).doFinal(ciphertext)
    }

    fun decrypt(key: ByteArray, ciphertext: InputStream, iv: ByteArray, aad: ByteArray? = null): InputStream {
        return CipherInputStream(ciphertext, getDecryptCipher(key, iv, aad))
    }
}
