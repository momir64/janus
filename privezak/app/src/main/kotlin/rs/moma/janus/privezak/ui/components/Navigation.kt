package rs.moma.janus.privezak.ui.components

import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.pager.HorizontalPager
import rs.moma.janus.privezak.ui.screens.SettingsScreen
import rs.moma.janus.privezak.ui.dialogs.ConfirmDialog
import rs.moma.janus.privezak.ui.screens.WelcomeScreen
import rs.moma.janus.privezak.viewmodels.MainViewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalFocusManager
import rs.moma.janus.privezak.security.biometricIssue
import rs.moma.janus.privezak.ui.screens.UnlockScreen
import rs.moma.janus.privezak.ui.theme.CardBackground
import rs.moma.janus.privezak.ui.screens.SetupScreen
import rs.moma.janus.privezak.security.authenticate
import rs.moma.janus.privezak.ui.screens.HomeScreen
import rs.moma.janus.privezak.ui.screens.ScanScreen
import rs.moma.janus.privezak.ui.utils.SingleToast
import androidx.compose.foundation.layout.padding
import rs.moma.janus.privezak.security.AuthResult
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Box
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.BackHandler
import rs.moma.janus.privezak.ui.theme.Muted
import androidx.lifecycle.repeatOnLifecycle
import android.content.Intent.ACTION_VIEW
import android.annotation.SuppressLint
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import android.provider.Settings
import kotlinx.coroutines.launch
import androidx.core.net.toUri
import android.content.Intent

private enum class Page { Home, Settings }

@Composable
fun Navigation(vm: MainViewModel) {
    val scope = rememberCoroutineScope()
    val activity = LocalActivity.current as FragmentActivity
    val biometricEnabled by vm.isBiometricEnabled.collectAsState()
    var welcomed by rememberSaveable { mutableStateOf(false) }
    var setupHintHidden by remember { mutableStateOf(false) }
    val sessionTimeout by vm.sessionTimeout.collectAsState()
    val needsSetupHint by vm.needsSetupHint.collectAsState()
    val pager = rememberPagerState { Page.entries.size }
    var scanning by remember { mutableStateOf(false) }
    val isUnlocked by vm.isUnlocked.collectAsState()
    val passkeys by vm.passkeys.collectAsState()
    val isSetUp by vm.isSetUp.collectAsState()

    LaunchedEffect(isUnlocked) {
        if (isUnlocked != true) {
            pager.scrollToPage(Page.Home.ordinal)
            setupHintHidden = false
            scanning = false
        }
    }

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
        val top = if (scanning) 0.dp else innerPadding.calculateTopPadding()
        Box(Modifier.padding(top = top)) {
            when {
                isUnlocked == true && scanning -> ScanScreen(
                    onBack = { scanning = false },
                    onScanned = { link ->
                        scanning = false
                        runCatching { activity.startActivity(Intent(ACTION_VIEW, link.toUri())) }
                            .onFailure { SingleToast.show(activity, "Could not open the code") }
                    }
                )
                isUnlocked == true -> HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    if (page == Page.Home.ordinal) HomeScreen(
                        passkeys = passkeys,
                        onSettings = { scope.launch { pager.scrollToPage(Page.Settings.ordinal) } },
                        onScan = { scanning = true },
                        onDelete = vm::deletePasskey
                    )
                    else SettingsScreen(
                        onBack = { scope.launch { pager.scrollToPage(Page.Home.ordinal) } },
                        biometricEnabled = biometricEnabled,
                        biometricIssue = biometricIssue,
                        onEnableBiometric = { enableBiometric(vm, activity) },
                        onDisableBiometric = vm::disableBiometric,
                        sessionTimeout = sessionTimeout,
                        onSessionTimeout = vm::setSessionTimeout,
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

            if (isUnlocked == true && needsSetupHint && !setupHintHidden) ConfirmDialog(
                title = "Configuration",
                text = "To use privezak you need to add it as a credential manager in the settings.",
                confirmLabel = "OK",
                dismissMessage = "Dismiss",
                dismissColor = Muted,
                dismissBackgroundColor = CardBackground,
                confirmColor = MaterialTheme.colorScheme.primary,
                onConfirm = {
                    vm.dismissSetupHint()
                    runCatching { activity.startActivity(credentialSettings(activity)) }
                },
                onDismiss = vm::dismissSetupHint,
                onDismissRequest = { setupHintHidden = true }
            )
        }
    }
}

@SuppressLint("InlinedApi")
private fun credentialSettings(activity: FragmentActivity) =
    Intent(Settings.ACTION_CREDENTIAL_PROVIDER, "package:${activity.packageName}".toUri())

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

internal suspend fun unlockWithBiometric(vm: MainViewModel, activity: FragmentActivity): String? {
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
