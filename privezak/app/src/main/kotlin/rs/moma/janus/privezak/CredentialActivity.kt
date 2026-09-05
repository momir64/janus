package rs.moma.janus.privezak

import rs.moma.janus.privezak.ui.components.unlockWithBiometric
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import rs.moma.janus.privezak.provider.EXTRA_CREDENTIAL_ID
import androidx.credentials.provider.PendingIntentHandler
import rs.moma.janus.privezak.provider.registrationResult
import rs.moma.janus.privezak.provider.assertionResult
import rs.moma.janus.privezak.viewmodels.MainViewModel
import rs.moma.janus.privezak.ui.dialogs.UnlockDialog
import rs.moma.janus.privezak.provider.entriesResult
import rs.moma.janus.privezak.ui.theme.PrivezakTheme
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import android.os.Bundle

class CredentialActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    private val chosenCredential: String? by lazy { intent.getStringExtra(EXTRA_CREDENTIAL_ID) }

    private val createRequest by lazy {
        PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
    }

    private val getRequest by lazy {
        PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
    }

    private val beginGetRequest by lazy {
        PendingIntentHandler.retrieveBeginGetCredentialRequest(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(FLAG_SECURE, FLAG_SECURE)

        setContent {
            PrivezakTheme {
                val biometricEnabled by vm.isBiometricEnabled.collectAsState()
                val isUnlocked by vm.isUnlocked.collectAsState()

                LaunchedEffect(Unit) { vm.unlockFromSession() }

                LaunchedEffect(isUnlocked) {
                    if (isUnlocked != true) return@LaunchedEffect
                    val chosen = chosenCredential
                    val create = createRequest
                    val result = when {
                        chosen != null -> getRequest?.let { assertionResult(it, chosen, vm) }
                        create != null -> registrationResult(create, vm)
                        else -> entriesResult(
                            this@CredentialActivity,
                            beginGetRequest,
                            vm.passkeys.value
                        )
                    }
                    result?.let { setResult(RESULT_OK, it) }
                    finish()
                }

                if (isUnlocked == false) UnlockDialog(
                    onUnlockWithBiometric = { unlockWithBiometric(vm, this@CredentialActivity) },
                    biometricEnabled = biometricEnabled,
                    onUnlock = vm::unlock,
                    onDismiss = ::finish
                )
            }
        }
    }
}
