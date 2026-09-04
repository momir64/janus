package rs.moma.privezak.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.input.ImeAction
import rs.moma.privezak.security.MIN_PIN_LENGTH
import androidx.compose.ui.res.painterResource
import rs.moma.privezak.ui.components.PinField
import rs.moma.privezak.ui.theme.PrivezakTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import rs.moma.privezak.ui.utils.SingleToast
import rs.moma.privezak.ui.theme.Heading
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import rs.moma.privezak.R

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    biometricEnabled: Boolean,
    biometricIssue: String?,
    onEnableBiometric: suspend () -> String?,
    onDisableBiometric: () -> Unit,
    onChangePin: suspend (String) -> Boolean
) {
    var busy by remember { mutableStateOf(false) }
    val verify = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val new = rememberTextFieldState()

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    BackHandler(onBack = onBack)

    fun toggleBiometric() {
        if (biometricEnabled) {
            onDisableBiometric()
            return
        }
        busy = true
        scope.launch {
            try {
                onEnableBiometric()?.let { SingleToast.show(context, it) }
            } finally {
                busy = false
            }
        }
    }

    fun changePin() {
        if (new.text.length < MIN_PIN_LENGTH) {
            SingleToast.show(context, "PIN must be at least $MIN_PIN_LENGTH characters")
            return
        }
        if (!new.text.contentEquals(verify.text)) {
            SingleToast.show(context, "PINs do not match")
            return
        }
        busy = true
        scope.launch {
            if (onChangePin(new.text.toString())) {
                listOf(new, verify).forEach { it.edit { replace(0, length, "") } }
                focusManager.clearFocus()
                SingleToast.show(context, "PIN changed")
            } else {
                SingleToast.show(context, "Could not change PIN")
            }
            busy = false
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().imePadding().pointerInput(Unit) {
            detectTapGestures { focusManager.clearFocus() }
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
            }
            Spacer(Modifier.width(4.dp))
            Text("Settings", style = typography.headlineMedium, color = Heading)
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Unlock with Biometrics", style = typography.bodyLarge)
                Spacer(Modifier.weight(1f))
                Box {
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { toggleBiometric() },
                        enabled = biometricIssue == null && !busy
                    )
                    if (biometricIssue != null) Box(
                        Modifier.matchParentSize().clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { SingleToast.show(context, biometricIssue) }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text("Change PIN", style = typography.bodyLarge, color = Heading)
            Spacer(Modifier.height(16.dp))
            PinField(new, "New PIN", ImeAction.Next)
            Spacer(Modifier.height(8.dp))
            PinField(verify, "Verify new PIN", ImeAction.Done) { changePin() }

            Spacer(Modifier.height(24.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = !busy && new.text.isNotEmpty() && verify.text.isNotEmpty(),
                onClick = { changePin() }
            ) {
                Text("Save")
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    PrivezakTheme {
        Surface {
            SettingsScreen(
                onBack = {},
                biometricEnabled = false,
                biometricIssue = null,
                onEnableBiometric = { null },
                onDisableBiometric = {},
                onChangePin = { true }
            )
        }
    }
}
