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

    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn = _isLoggedIn.asStateFlow()

    private val _isBiometricEnabled = MutableStateFlow(vault.isBiometricEnabled)
    val isBiometricEnabled = _isBiometricEnabled.asStateFlow()

    private val _isSetUp = MutableStateFlow(vault.isSetUp)
    val isSetUp = _isSetUp.asStateFlow()

    suspend fun setUp(pin: String) = withContext(Dispatchers.Default) {
        dataKey = vault.setUp(pin)
        _isSetUp.value = true
        _isLoggedIn.value = true
    }

    suspend fun login(pin: String): Boolean = withContext(Dispatchers.Default) {
        val key = vault.unlock(pin) ?: return@withContext false
        dataKey = key
        _isLoggedIn.value = true
        true
    }

    fun loginWithBiometric(cipher: Cipher): Boolean {
        val key = vault.unlockWithBiometric(cipher) ?: return false
        dataKey = key
        _isLoggedIn.value = true
        return true
    }

    fun logout() {
        _isLoggedIn.value = false
        dataKey = null
    }

    suspend fun changePin(current: String, new: String): Boolean =
        withContext(Dispatchers.Default) { vault.changePin(current, new) }

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