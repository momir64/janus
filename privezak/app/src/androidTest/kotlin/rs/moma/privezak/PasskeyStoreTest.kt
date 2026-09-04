package rs.moma.privezak

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.security.keystore.KeyProperties.*
import rs.moma.privezak.security.PasskeyStore
import android.security.keystore.KeyInfo
import rs.moma.privezak.security.decrypt
import rs.moma.privezak.security.encrypt
import android.os.Build.VERSION.SDK_INT
import javax.crypto.spec.SecretKeySpec
import android.content.ContextWrapper
import java.security.KeyFactory
import java.security.PrivateKey
import org.junit.runner.RunWith
import java.security.Signature
import java.security.KeyStore
import org.junit.Assert.*
import android.os.Bundle
import org.junit.Test
import java.io.File

@RunWith(AndroidJUnit4::class)
class PasskeyStoreTest {
    private val target = InstrumentationRegistry.getInstrumentation().targetContext

    private val context = object : ContextWrapper(target) {
        private val dir = File(baseContext.cacheDir, "passkey-test").apply { mkdirs() }
        override fun getFilesDir() = dir
    }

    private val dataKey = ByteArray(32) { it.toByte() }

    @Test
    fun encryptsWithA12ByteIvAnd16ByteTag() {
        val plaintext = ByteArray(50)
        val stored = SecretKeySpec(dataKey, "AES").encrypt(plaintext)
        assertEquals(12 + plaintext.size + 16, stored.size)
        assertArrayEquals(plaintext, SecretKeySpec(dataKey, "AES").decrypt(stored))
    }

    @Test
    fun signsAssertionsAndDerivesPerCredentialSecrets() {
        val store = PasskeyStore(context, dataKey)
        val passkey = store.create("example.com", "Example", byteArrayOf(1, 2, 3), "test", "Test")
        try {
            assertEquals(listOf(passkey), PasskeyStore(context, dataKey).load())

            val data = "authenticator data".toByteArray()
            val verifier = Signature.getInstance("SHA256withECDSA").apply {
                initVerify(store.publicKey(passkey.id))
                update(data)
            }
            assertTrue(verifier.verify(store.sign(passkey.id, data)))

            val salt = ByteArray(32) { 7 }
            assertArrayEquals(
                store.hmacSecret(passkey.id, salt, uv = true),
                store.hmacSecret(passkey.id, salt, uv = true)
            )
            assertFalse(
                store.hmacSecret(passkey.id, salt, uv = true)
                    .contentEquals(store.hmacSecret(passkey.id, salt, uv = false))
            )

            assertEquals(1, store.recordUse(passkey.id))
            assertTrue(runCatching { PasskeyStore(context, ByteArray(32)).load() }.isFailure)

            assertSecureHardware(passkey.id)
        } finally {
            store.delete(passkey.id)
        }
        assertTrue(PasskeyStore(context, dataKey).load().isEmpty())
        assertFalse(keyStore().containsAlias("privezak_pk_${passkey.id}"))
    }

    private fun assertSecureHardware(id: String) {
        val info = keyInfo(id)
        if (SDK_INT < 31) {
            @Suppress("DEPRECATION")
            assertTrue("signing key is not in secure hardware", info.isInsideSecureHardware)
            return
        }
        report(
            when (val level = info.securityLevel) {
                SECURITY_LEVEL_STRONGBOX -> "StrongBox"
                SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> "trusted environment"
                SECURITY_LEVEL_SOFTWARE -> "software"
                SECURITY_LEVEL_UNKNOWN_SECURE -> "unspecified secure hardware"
                else -> "unknown ($level)"
            }
        )
        assertNotEquals(
            "signing key is software backed",
            SECURITY_LEVEL_SOFTWARE,
            info.securityLevel
        )
    }

    private fun report(level: String) = InstrumentationRegistry.getInstrumentation()
        .sendStatus(0, Bundle().apply { putString("stream", "\nsigning key: $level\n") })

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun keyInfo(id: String): KeyInfo {
        val key = keyStore().getKey("privezak_pk_$id", null) as PrivateKey
        return KeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
            .getKeySpec(key, KeyInfo::class.java)
    }
}
