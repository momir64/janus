package rs.moma.janus.privezak

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import rs.moma.janus.privezak.security.PasskeyStore
import android.security.keystore.KeyProperties.*
import rs.moma.janus.privezak.security.decrypt
import rs.moma.janus.privezak.security.encrypt
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import android.security.keystore.KeyInfo
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

    private val keyAttestationOid = "1.3.6.1.4.1.11129.2.1.17"
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
        val passkey = store.create(
            "example.com", "Example", byteArrayOf(1, 2, 3),
            "test", "Test", ByteArray(32)
        )
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
                store.hmacSecret(passkey.id, salt),
                store.hmacSecret(passkey.id, salt)
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

    @Test
    fun attestsTheSigningKeyOverTheGivenChallenge() {
        val store = PasskeyStore(context, dataKey)
        val challenge = ByteArray(32) { (it * 7).toByte() }
        val passkey =
            store.create("example.com", "Example", byteArrayOf(1), "test", "Test", challenge)
        try {
            val chain = store.certificateChain(passkey.id)
            if (chain.size < 2) {
                report("no attestation available, statement falls back to none")
                return
            }

            val leaf = CertificateFactory.getInstance("X.509")
                .generateCertificate(chain.first().inputStream()) as X509Certificate
            val description = leaf.getExtensionValue(keyAttestationOid)
            assertNotNull("no key attestation extension", description)
            assertTrue(
                "challenge is not in the attestation",
                description!!.toList().windowed(challenge.size).any { it == challenge.toList() }
            )
            report("attestation chain of ${chain.size}, subject ${leaf.subjectX500Principal}")
        } finally {
            store.delete(passkey.id)
        }
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
