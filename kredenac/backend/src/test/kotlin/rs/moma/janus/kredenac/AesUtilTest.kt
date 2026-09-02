package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.crypto.algorithms.AesUtil
import javax.crypto.AEADBadTagException
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertEquals
import kotlin.test.Test

class AesUtilTest {
    private val key = AesUtil.generateKey()
    private val plaintext = "someone@example.com".toByteArray()

    @Test
    fun `round trips with and without additional data`() {
        val plain = AesUtil.encrypt(key, plaintext)
        assertContentEquals(plaintext, AesUtil.decrypt(key, plain.ciphertext, plain.iv))

        val aad = "token-key".toByteArray()
        val bound = AesUtil.encrypt(key, plaintext, aad)
        assertContentEquals(plaintext, AesUtil.decrypt(key, bound.ciphertext, bound.iv, aad))
    }

    @Test
    fun `a value cannot be moved to a record with different additional data`() {
        val encrypted = AesUtil.encrypt(key, plaintext, "challenge-a".toByteArray())

        assertFailsWith<AEADBadTagException> {
            AesUtil.decrypt(key, encrypted.ciphertext, encrypted.iv, "challenge-b".toByteArray())
        }
        assertFailsWith<AEADBadTagException> {
            AesUtil.decrypt(key, encrypted.ciphertext, encrypted.iv)
        }
    }

    @Test
    fun `a tampered ciphertext or a wrong key is rejected, never returned`() {
        val encrypted = AesUtil.encrypt(key, plaintext)

        val flipped = encrypted.ciphertext.copyOf()
        flipped[0] = (flipped[0].toInt() xor 1).toByte()
        assertFailsWith<AEADBadTagException> { AesUtil.decrypt(key, flipped, encrypted.iv) }

        assertFailsWith<AEADBadTagException> {
            AesUtil.decrypt(AesUtil.generateKey(), encrypted.ciphertext, encrypted.iv)
        }
    }

    @Test
    fun `every encryption draws a fresh iv`() {
        val ivs = (1..50).map { AesUtil.encrypt(key, plaintext).iv.toList() }.toSet()
        assertEquals(50, ivs.size)
        assertNotEquals(
            AesUtil.encrypt(key, plaintext).ciphertext.toList(),
            AesUtil.encrypt(key, plaintext).ciphertext.toList()
        )
    }

    @Test
    fun `ciphertext grows by exactly the tag length that uploads reserve`() {
        for (size in listOf(0, 1, 15, 16, 1024)) {
            val encrypted = AesUtil.encrypt(key, ByteArray(size))
            assertEquals(size + AesUtil.GCM_TAG_LENGTH_BYTES, encrypted.ciphertext.size, "plaintext of $size")
        }
    }

    @Test
    fun `round trips a stream, the way file contents are stored`() {
        val content = ByteArray(4096) { (it % 251).toByte() }
        val aad = "file-id".toByteArray()

        val encrypted = AesUtil.encrypt(key, content.inputStream(), aad)
        val ciphertext = encrypted.stream.use { it.readBytes() }
        assertEquals(content.size + AesUtil.GCM_TAG_LENGTH_BYTES, ciphertext.size)

        val decrypted = AesUtil.decrypt(key, ciphertext.inputStream(), encrypted.iv, aad)
        assertContentEquals(content, decrypted.use { it.readBytes() })
    }
}
