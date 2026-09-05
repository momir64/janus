package rs.moma.janus.privezak.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties.*
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.Cipher.DECRYPT_MODE
import javax.crypto.Cipher.ENCRYPT_MODE
import javax.crypto.spec.SecretKeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import androidx.core.content.edit
import javax.crypto.KeyGenerator
import kotlin.io.encoding.Base64
import android.content.Context
import java.security.KeyStore
import javax.crypto.SecretKey
import javax.crypto.Cipher

// Keystore key aliases
private const val BIOMETRIC_KEYSTORE_ALIAS = "privezak_biometric"
private const val BINDING_KEYSTORE_ALIAS = "privezak_pin"

// Prefs keys
private const val LIST_SESSION_TIMEOUT = "list_session_timeout"
private const val BY_BIOMETRIC_IV = "key_by_biometric_iv"
private const val SETUP_HINT_SEEN = "setup_hint_seen"
private const val BY_BIOMETRIC = "key_by_biometric"
private const val BY_PIN = "key_by_pin"
private const val PREFS = "privezak"
private const val SALT = "pin_salt"

// KDF number of iterations
private const val ITERATIONS = 220_000

const val MIN_PIN_LENGTH = 6

class PinVault(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val isBiometricEnabled: Boolean get() = prefs.contains(BY_BIOMETRIC)
    val isSetUp: Boolean get() = prefs.contains(BY_PIN)

    var isSetupHintSeen: Boolean
        get() = prefs.getBoolean(SETUP_HINT_SEEN, false)
        set(value) = prefs.edit(commit = true) { putBoolean(SETUP_HINT_SEEN, value) }

    var sessionTimeout: SessionTimeout
        get() = prefs.getString(LIST_SESSION_TIMEOUT, null)
            ?.let { stored -> SessionTimeout.entries.find { it.name == stored } }
            ?: SessionTimeout.Immediately
        set(value) = prefs.edit(commit = true) { putString(LIST_SESSION_TIMEOUT, value.name) }

    fun setUp(pin: String): ByteArray {
        val dataKey = randomBytes(KEY_BITS / 8)
        changePin(dataKey, pin)
        return dataKey
    }

    fun unlock(pin: String): ByteArray? {
        val salt = prefs.getString(SALT, null)?.let(Base64::decode) ?: return null
        val wrapped = prefs.getString(BY_PIN, null)?.let(Base64::decode) ?: return null
        return runCatching { derive(pin, salt).decrypt(unbind(wrapped)) }.getOrNull()
    }

    fun changePin(dataKey: ByteArray, pin: String) {
        val salt = randomBytes(16)
        prefs.edit(commit = true) {
            putString(BY_PIN, Base64.encode(bind(derive(pin, salt).encrypt(dataKey))))
            putString(SALT, Base64.encode(salt))
        }
    }

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    fun enrollBiometricCipher(): Cipher = Cipher.getInstance(TRANSFORMATION).apply {
        keyStore().deleteEntry(BIOMETRIC_KEYSTORE_ALIAS)
        val generator = KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(
                BIOMETRIC_KEYSTORE_ALIAS,
                PURPOSE_ENCRYPT or PURPOSE_DECRYPT
            )
                .setBlockModes(BLOCK_MODE_GCM)
                .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
                .setUserAuthenticationRequired(true)
                .setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)
                .setInvalidatedByBiometricEnrollment(true)
                .build()
        )
        init(ENCRYPT_MODE, generator.generateKey())
    }

    fun unlockBiometricCipher(): Cipher? {
        val iv = prefs.getString(BY_BIOMETRIC_IV, null)?.let(Base64::decode) ?: return null
        val key = keyStore().getKey(BIOMETRIC_KEYSTORE_ALIAS, null) as? SecretKey ?: return null
        return runCatching {
            Cipher.getInstance(TRANSFORMATION).apply {
                init(DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            }
        }.getOrNull()
    }

    fun enableBiometric(dataKey: ByteArray, cipher: Cipher) {
        prefs.edit(commit = true) {
            putString(BY_BIOMETRIC, Base64.encode(cipher.doFinal(dataKey)))
            putString(BY_BIOMETRIC_IV, Base64.encode(cipher.iv))
        }
    }

    fun unlockWithBiometric(cipher: Cipher): ByteArray? {
        val wrapped = prefs.getString(BY_BIOMETRIC, null)?.let(Base64::decode) ?: return null
        return runCatching { cipher.doFinal(wrapped) }.getOrNull()
    }

    fun disableBiometric() {
        prefs.edit { remove(BY_BIOMETRIC).remove(BY_BIOMETRIC_IV) }
        runCatching { keyStore().deleteEntry(BIOMETRIC_KEYSTORE_ALIAS) }
    }

    private fun derive(pin: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512")
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    // Binding the data key to this device so that it can't be brute forced offline
    private fun bindingKey(): SecretKey {
        keyStore().getKey(BINDING_KEYSTORE_ALIAS, null)?.let { return it as SecretKey }
        val generator = KeyGenerator.getInstance(KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(BINDING_KEYSTORE_ALIAS, PURPOSE_ENCRYPT or PURPOSE_DECRYPT)
                .setBlockModes(BLOCK_MODE_GCM)
                .setEncryptionPaddings(ENCRYPTION_PADDING_NONE)
                .build()
        )
        return generator.generateKey()
    }

    private fun bind(plaintext: ByteArray): ByteArray = bindingKey().encrypt(plaintext, true)

    private fun unbind(stored: ByteArray): ByteArray = bindingKey().decrypt(stored)
}
