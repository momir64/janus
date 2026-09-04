package rs.moma.privezak.ui.components

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import rs.moma.privezak.ui.utils.SingleToast
import androidx.lifecycle.repeatOnLifecycle
import rs.moma.privezak.ui.theme.Heading
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import kotlinx.coroutines.launch

@Composable
fun UnlockContent(
    biometricEnabled: Boolean,
    onUnlock: suspend (String) -> Boolean,
    onUnlockWithBiometric: suspend () -> String?,
    modifier: Modifier = Modifier
) {
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pin = rememberTextFieldState()

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val context = LocalContext.current

    suspend fun unlockWithBiometric() {
        busy = true
        try {
            onUnlockWithBiometric()?.let { SingleToast.show(context, it) }
        } finally {
            busy = false
        }
    }

    fun unlock() {
        busy = true
        scope.launch {
            if (!onUnlock(pin.text.toString())) SingleToast.show(context, "Incorrect PIN")
            busy = false
        }
    }

    LaunchedEffect(biometricEnabled) {
        if (!biometricEnabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { unlockWithBiometric() }
    }

    Column(modifier) {
        PinField(pin, "PIN", ImeAction.Done) { unlock() }
        Spacer(Modifier.height(32.dp))

        Button(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !busy && pin.text.isNotEmpty(),
            onClick = { unlock() }
        ) {
            Text("Unlock")
        }

        if (biometricEnabled) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Heading),
                enabled = !busy,
                onClick = { scope.launch { unlockWithBiometric() } }
            ) {
                Text("Unlock with biometrics")
            }
        }
    }
}
