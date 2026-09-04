package rs.moma.privezak.viewmodels

import kotlinx.coroutines.flow.MutableStateFlow
import rs.moma.privezak.security.PasskeyStore
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.asStateFlow
import rs.moma.privezak.security.PinVault
import rs.moma.privezak.security.Passkey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.Application
import javax.crypto.Cipher

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val vault = PinVault(application)
    private var store: PasskeyStore? = null
    private var dataKey: ByteArray? = null

    private val _isUnlocked = MutableStateFlow<Boolean?>(null)
    val isUnlocked = _isUnlocked.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(vault.isBiometricEnabled)
    val isBiometricEnabled = _isBiometricEnabled.asStateFlow()

    private val _isSetUp = MutableStateFlow(vault.isSetUp)
    val isSetUp = _isSetUp.asStateFlow()

    private val _passkeys = MutableStateFlow(emptyList<Passkey>())
    val passkeys = _passkeys.asStateFlow()

    suspend fun setUp(pin: String) = withContext(Dispatchers.Default) {
        open(vault.setUp(pin))
        _isSetUp.value = true
    }

    suspend fun unlock(pin: String): Boolean = withContext(Dispatchers.Default) {
        open(vault.unlock(pin) ?: return@withContext false)
        true
    }

    fun unlockWithBiometric(cipher: Cipher): Boolean {
        open(vault.unlockWithBiometric(cipher) ?: return false)
        return true
    }

    fun lock() {
        _isUnlocked.value = false
        _passkeys.value = emptyList()
        dataKey = null
        store = null
    }

    suspend fun deletePasskey(id: String) = withContext(Dispatchers.Default) {
        val store = store ?: return@withContext
        store.delete(id)
        _passkeys.value = store.load()
    }

    private fun open(key: ByteArray) {
        dataKey = key
        store = PasskeyStore(getApplication(), key).also { _passkeys.value = it.load() }
        _isUnlocked.value = true
    }

    suspend fun changePin(pin: String): Boolean = withContext(Dispatchers.Default) {
        val key = dataKey ?: return@withContext false
        vault.changePin(key, pin)
        true
    }

    fun unlockBiometricCipher(): Cipher? = vault.unlockBiometricCipher()
    fun enrollBiometricCipher(): Cipher = vault.enrollBiometricCipher()

    fun enableBiometric(cipher: Cipher) {
        val key = dataKey ?: return
        vault.enableBiometric(key, cipher)
        _isBiometricEnabled.value = true
    }

    fun disableBiometric() {
        vault.disableBiometric()
        _isBiometricEnabled.value = false
    }
}