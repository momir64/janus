package rs.moma.privezak

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import androidx.compose.material3.MaterialTheme.typography
import androidx.credentials.provider.PendingIntentHandler
import rs.moma.privezak.ui.components.unlockWithBiometric
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import rs.moma.privezak.provider.EXTRA_CREDENTIAL_ID
import rs.moma.privezak.provider.registrationResult
import rs.moma.privezak.ui.components.UnlockContent
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import rs.moma.privezak.provider.assertionResult
import rs.moma.privezak.viewmodels.MainViewModel
import rs.moma.privezak.ui.theme.CardBackground
import rs.moma.privezak.provider.entriesResult
import rs.moma.privezak.ui.theme.PrivezakTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.clickable
import androidx.activity.compose.setContent
import androidx.compose.ui.graphics.Color
import rs.moma.privezak.ui.theme.Heading
import androidx.compose.ui.Alignment
import androidx.activity.viewModels
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
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

                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = ::finish,
                            indication = null
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.systemBars)
                            .imePadding()
                            .padding(24.dp),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = CardBackground)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("PRIVEZAK", style = typography.headlineMedium, color = Heading)
                            Spacer(Modifier.height(20.dp))
                            UnlockContent(
                                biometricEnabled = biometricEnabled,
                                onUnlock = vm::unlock,
                                onUnlockWithBiometric = {
                                    unlockWithBiometric(vm, this@CredentialActivity)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}
