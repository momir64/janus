package rs.moma.privezak.security

import android.content.pm.PackageManager.FEATURE_STRONGBOX_KEYSTORE
import android.security.keystore.StrongBoxUnavailableException
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties.*
import java.security.spec.ECGenParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.Json
import java.security.KeyPairGenerator
import javax.crypto.KeyGenerator
import kotlin.io.encoding.Base64
import java.security.PrivateKey
import android.content.Context
import java.security.PublicKey
import java.security.Signature
import java.security.KeyStore
import javax.crypto.Mac
import java.io.File

private const val CREDENTIAL_ID_BYTES = 16
private const val CURVE = "secp256r1"
private const val FILE = "passkeys"

// A passkey is three Keystore entries and a record: the P-256 key that signs assertions, and one
// HMAC key per hmac-secret variant. None of the three can be read back out, so the record holds
// only what the extension and the UI need to know.
class PasskeyStore(context: Context, dataKey: ByteArray) {
    private val strongBox = context.packageManager.hasSystemFeature(FEATURE_STRONGBOX_KEYSTORE)
    private val file = File(context.filesDir, FILE)
    private val key = SecretKeySpec(dataKey, "AES")

    private fun signingAlias(id: String) = "privezak_pk_$id"
    private fun credRandomAlias(id: String, uv: Boolean) =
        "privezak_pk_${id}_${if (uv) "uv" else "nouv"}"

    fun load(): List<Passkey> =
        if (!file.exists()) emptyList()
        else Json.decodeFromString(key.decrypt(file.readBytes()).decodeToString())

    private fun save(passkeys: List<Passkey>) {
        val temp = File(file.parentFile, "$FILE.tmp")
        temp.writeBytes(key.encrypt(Json.encodeToString(passkeys).encodeToByteArray()))
        check(temp.renameTo(file)) { "Could not replace the passkey store" }
    }

    fun create(
        rpId: String,
        rpName: String,
        userHandle: ByteArray,
        userName: String,
        displayName: String
    ): Passkey {
        val id = Base64.UrlSafe.encode(randomBytes(CREDENTIAL_ID_BYTES))
        createSigningKey(signingAlias(id))
        createCredRandom(credRandomAlias(id, uv = true))
        createCredRandom(credRandomAlias(id, uv = false))

        val passkey = Passkey(
            id = id,
            rpId = rpId,
            rpName = rpName,
            userHandle = Base64.UrlSafe.encode(userHandle),
            userName = userName,
            displayName = displayName
        )
        save(load() + passkey)
        return passkey
    }

    fun delete(id: String) {
        val keyStore = keyStore()
        listOf(signingAlias(id), credRandomAlias(id, true), credRandomAlias(id, false))
            .forEach { runCatching { keyStore.deleteEntry(it) } }
        save(load().filterNot { it.id == id })
    }

    fun sign(id: String, data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withECDSA").run {
            initSign(keyStore().getKey(signingAlias(id), null) as PrivateKey)
            update(data)
            sign()
        }

    fun recordUse(id: String): Int {
        val passkeys = load()
        val used = passkeys.first { it.id == id }.let { it.copy(signCount = it.signCount + 1) }
        save(passkeys.map { if (it.id == used.id) used else it })
        return used.signCount
    }

    fun hmacSecret(id: String, salt: ByteArray, uv: Boolean): ByteArray =
        Mac.getInstance(KEY_ALGORITHM_HMAC_SHA256).run {
            init(keyStore().getKey(credRandomAlias(id, uv), null))
            doFinal(salt)
        }

    fun publicKey(id: String): PublicKey = keyStore().getCertificate(signingAlias(id)).publicKey

    private fun keyStore() = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    private fun createSigningKey(alias: String) = withStrongBoxFallback { strongBox ->
        KeyPairGenerator.getInstance(KEY_ALGORITHM_EC, "AndroidKeyStore").run {
            initialize(
                KeyGenParameterSpec.Builder(alias, PURPOSE_SIGN)
                    .setIsStrongBoxBacked(strongBox)
                    .setAlgorithmParameterSpec(ECGenParameterSpec(CURVE))
                    .setDigests(DIGEST_SHA256)
                    .build()
            )
            generateKeyPair()
        }
    }

    private fun createCredRandom(alias: String) = withStrongBoxFallback { strongBox ->
        KeyGenerator.getInstance(KEY_ALGORITHM_HMAC_SHA256, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(alias, PURPOSE_SIGN)
                    .setIsStrongBoxBacked(strongBox)
                    .setKeySize(KEY_BITS)
                    .build()
            )
            generateKey()
        }
    }

    private fun <T> withStrongBoxFallback(generate: (Boolean) -> T): T =
        if (!strongBox) generate(false)
        else try {
            generate(true)
        } catch (_: StrongBoxUnavailableException) {
            generate(false)
        }
}
