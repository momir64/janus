package rs.moma.privezak.ui.components

import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import rs.moma.privezak.ui.screens.RegisterScreen
import rs.moma.privezak.ui.screens.WelcomeScreen
import rs.moma.privezak.viewmodels.MainViewModel
import androidx.activity.compose.LocalActivity
import rs.moma.privezak.ui.screens.LoginScreen
import androidx.compose.foundation.layout.Box
import androidx.fragment.app.FragmentActivity
import rs.moma.privezak.security.authenticate
import rs.moma.privezak.ui.screens.HomeScreen
import rs.moma.privezak.security.AuthResult
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun Navigation(vm: MainViewModel) {
    val activity = LocalActivity.current as FragmentActivity
    var welcomed by rememberSaveable { mutableStateOf(false) }
    val biometricEnabled by vm.isBiometricEnabled.collectAsState()
    val isLoggedIn by vm.isLoggedIn.collectAsState()
    val isSetUp by vm.isSetUp.collectAsState()

    Scaffold(Modifier.fillMaxSize()) { innerPadding ->
        Box(Modifier.padding(top = innerPadding.calculateTopPadding())) {
            when {
                isLoggedIn == true -> HomeScreen()
                !isSetUp && !welcomed -> WelcomeScreen { welcomed = true }
                !isSetUp -> RegisterScreen(onBack = { welcomed = false }, onConfirm = vm::setUp)
                else -> LoginScreen(
                    biometricEnabled = biometricEnabled,
                    onLogin = vm::login,
                    onUnlockWithBiometric = { unlockWithBiometric(vm, activity) }
                )
            }
        }
    }
}

private suspend fun unlockWithBiometric(vm: MainViewModel, activity: FragmentActivity): String? {
    val failedMessage = "Biometric unlock failed"
    val cipher = vm.unlockBiometricCipher() ?: run {
        vm.disableBiometric()
        return "Biometric unlock is no longer available"
    }
    return when (val result = activity.authenticate(cipher, "Unlock Privezak")) {
        is AuthResult.Success -> if (vm.loginWithBiometric(result.cipher)) null else failedMessage
        is AuthResult.Failed -> result.message
        AuthResult.Cancelled -> null
    }
}
