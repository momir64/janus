package rs.moma.privezak.ui.screens

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.activity.compose.LocalActivity
import rs.moma.privezak.ui.components.PinField
import rs.moma.privezak.ui.theme.PrivezakTheme
import androidx.compose.foundation.layout.ime
import rs.moma.privezak.ui.utils.SingleToast
import androidx.lifecycle.repeatOnLifecycle
import androidx.activity.ComponentActivity
import androidx.compose.ui.unit.IntOffset
import rs.moma.privezak.ui.theme.Heading
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnlockScreen(
    biometricEnabled: Boolean,
    onUnlock: suspend (String) -> Boolean,
    onUnlockWithBiometric: suspend () -> String?
) {
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pin = rememberTextFieldState()

    val lifecycle = (LocalActivity.current as ComponentActivity).lifecycle
    val focusManager = LocalFocusManager.current
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

    val lift = with(LocalDensity.current) { 56.dp.roundToPx() }
    val imeFrom = WindowInsets.imeAnimationSource
    val imeTo = WindowInsets.imeAnimationTarget
    val ime = WindowInsets.ime
    fun Density.imeLift(): IntOffset {
        val travel = maxOf(imeFrom.getBottom(this), imeTo.getBottom(this))
        val shown = (ime.getBottom(this).toFloat() / travel.coerceAtLeast(1)).coerceIn(0f, 1f)
        return IntOffset(0, -(lift * (1f - shown)).toInt())
    }

    LaunchedEffect(biometricEnabled) {
        if (!biometricEnabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { unlockWithBiometric() }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .imePadding().padding(24.dp)
            .offset { imeLift() }
            .pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus() }
            },
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PRIVEZAK", style = typography.displayMedium, color = Heading)
        Spacer(Modifier.height(36.dp))
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

@Preview(showSystemUi = true)
@Composable
private fun UnlockScreenPreview() {
    PrivezakTheme {
        Surface {
            UnlockScreen(
                biometricEnabled = true,
                onUnlock = { true },
                onUnlockWithBiometric = { null })
        }
    }
}
