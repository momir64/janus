package rs.moma.privezak.ui.components

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.padding
import rs.moma.privezak.ui.screens.SettingsScreen
import rs.moma.privezak.ui.screens.WelcomeScreen
import rs.moma.privezak.viewmodels.MainViewModel
import rs.moma.privezak.security.biometricIssue
import rs.moma.privezak.ui.screens.UnlockScreen
import androidx.activity.compose.LocalActivity
import rs.moma.privezak.ui.screens.SetupScreen
import androidx.compose.foundation.layout.Box
import androidx.fragment.app.FragmentActivity
import rs.moma.privezak.security.authenticate
import rs.moma.privezak.ui.screens.HomeScreen
import androidx.activity.compose.BackHandler
import androidx.lifecycle.repeatOnLifecycle
import rs.moma.privezak.security.AuthResult
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch

private enum class Page { Home, Settings }

@Composable
fun Navigation(vm: MainViewModel) {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current as FragmentActivity
    val biometricEnabled by vm.isBiometricEnabled.collectAsState()
    var welcomed by rememberSaveable { mutableStateOf(false) }
    val pager = rememberPagerState { Page.entries.size }
    val isUnlocked by vm.isUnlocked.collectAsState()
    val passkeys by vm.passkeys.collectAsState()
    val isSetUp by vm.isSetUp.collectAsState()

    LaunchedEffect(isUnlocked) { if (isUnlocked != true) pager.scrollToPage(Page.Home.ordinal) }

    val focusManager = LocalFocusManager.current
    LaunchedEffect(pager.currentPage) {
        if (pager.currentPage == Page.Home.ordinal) focusManager.clearFocus()
    }

    BackHandler(enabled = pager.currentPage != Page.Home.ordinal) {
        scope.launch { pager.scrollToPage(Page.Home.ordinal) }
    }

    var biometricIssue by remember { mutableStateOf(activity.biometricIssue()) }
    LaunchedEffect(Unit) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            biometricIssue = activity.biometricIssue()
        }
    }

    Scaffold(Modifier.fillMaxSize()) { innerPadding ->
        Box(Modifier.padding(top = innerPadding.calculateTopPadding())) {
            when {
                isUnlocked == true -> HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    if (page == Page.Home.ordinal) HomeScreen(
                        passkeys = passkeys,
                        onSettings = { scope.launch { pager.scrollToPage(Page.Settings.ordinal) } },
                        onScan = { },
                        onDelete = vm::deletePasskey
                    )
                    else SettingsScreen(
                        onBack = { scope.launch { pager.scrollToPage(Page.Home.ordinal) } },
                        biometricEnabled = biometricEnabled,
                        biometricIssue = biometricIssue,
                        onEnableBiometric = { enableBiometric(vm, activity) },
                        onDisableBiometric = vm::disableBiometric,
                        onChangePin = vm::changePin
                    )
                }
                !isSetUp && !welcomed -> WelcomeScreen { welcomed = true }
                !isSetUp -> SetupScreen(onBack = { welcomed = false }, onConfirm = vm::setUp)
                else -> UnlockScreen(
                    biometricEnabled = biometricEnabled,
                    onUnlock = vm::unlock,
                    onUnlockWithBiometric = { unlockWithBiometric(vm, activity) }
                )
            }
        }
    }
}

private suspend fun enableBiometric(vm: MainViewModel, activity: FragmentActivity): String? {
    val promptTitle = "Enable biometric unlock"
    return when (val result = activity.authenticate(vm.enrollBiometricCipher(), promptTitle)) {
        is AuthResult.Success -> {
            vm.enableBiometric(result.cipher)
            null
        }
        is AuthResult.Failed, AuthResult.Cancelled -> {
            vm.disableBiometric()
            (result as? AuthResult.Failed)?.message
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
        is AuthResult.Success -> if (vm.unlockWithBiometric(result.cipher)) null else failedMessage
        is AuthResult.Failed -> result.message
        AuthResult.Cancelled -> null
    }
}
