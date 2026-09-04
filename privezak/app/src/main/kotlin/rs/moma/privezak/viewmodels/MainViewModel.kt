package rs.moma.privezak.viewmodels

import kotlinx.coroutines.flow.MutableStateFlow
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.asStateFlow
import rs.moma.privezak.security.PinVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.Application
import javax.crypto.Cipher

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val vault = PinVault(application)
    private var dataKey: ByteArray? = null

    private val _isUnlocked = MutableStateFlow<Boolean?>(null)
    val isUnlocked = _isUnlocked.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(vault.isBiometricEnabled)
    val isBiometricEnabled = _isBiometricEnabled.asStateFlow()

    private val _isSetUp = MutableStateFlow(vault.isSetUp)
    val isSetUp = _isSetUp.asStateFlow()

    suspend fun setUp(pin: String) = withContext(Dispatchers.Default) {
        dataKey = vault.setUp(pin)
        _isSetUp.value = true
        _isUnlocked.value = true
    }

    suspend fun unlock(pin: String): Boolean = withContext(Dispatchers.Default) {
        val key = vault.unlock(pin) ?: return@withContext false
        dataKey = key
        _isUnlocked.value = true
        true
    }

    fun unlockWithBiometric(cipher: Cipher): Boolean {
        val key = vault.unlockWithBiometric(cipher) ?: return false
        dataKey = key
        _isUnlocked.value = true
        return true
    }

    fun lock() {
        _isUnlocked.value = false
        dataKey = null
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