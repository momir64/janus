package rs.moma.privezak.ui.screens

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.input.ImeAction
import rs.moma.privezak.security.MIN_PIN_LENGTH
import rs.moma.privezak.security.SessionTimeout
import rs.moma.privezak.ui.theme.CardBackground
import androidx.compose.ui.res.painterResource
import rs.moma.privezak.ui.components.Dropdown
import rs.moma.privezak.ui.components.PinField
import rs.moma.privezak.ui.theme.PrivezakTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ime
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
    sessionTimeout: SessionTimeout,
    onSessionTimeout: (SessionTimeout) -> Unit,
    onChangePin: suspend (String) -> Boolean
) {
    var busy by remember { mutableStateOf(false) }
    val verify = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val new = rememberTextFieldState()

    val scroll = rememberScrollState()
    val density = LocalDensity.current
    val ime = WindowInsets.ime
    LaunchedEffect(Unit) {
        snapshotFlow { ime.getBottom(density) to scroll.maxValue }
            .collect { (imeHeight, bottom) -> if (imeHeight > 0) scroll.scrollTo(bottom) }
    }

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

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

        Column(
            modifier = Modifier.weight(1f)
                .verticalScroll(scroll)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            SettingsCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Unlock with Biometrics", style = typography.bodyLarge)
                    Spacer(Modifier.weight(1f))
                    Box {
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { if (!busy) toggleBiometric() }
                        )
                        if (biometricIssue != null) Box(
                            Modifier.matchParentSize().clickable(
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            ) { SingleToast.show(context, biometricIssue) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            SettingsCard {
                Spacer(Modifier.height(2.dp))
                Text("List passkeys unlock timeout", style = typography.bodyLarge, color = Heading)
                Spacer(Modifier.height(6.dp))
                Dropdown(
                    modifier = Modifier.fillMaxWidth(),
                    values = SessionTimeout.entries,
                    onSelect = onSessionTimeout,
                    selected = sessionTimeout
                )
                Spacer(Modifier.height(6.dp))
            }
            Spacer(Modifier.height(16.dp))

            SettingsCard {
                Spacer(Modifier.height(6.dp))
                Text("Change PIN", style = typography.bodyLarge, color = Heading)
                Spacer(Modifier.height(12.dp))
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
                Spacer(Modifier.height(10.dp))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 22.dp),
            content = content
        )
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
                sessionTimeout = SessionTimeout.Immediately,
                onSessionTimeout = {},
                onChangePin = { true }
            )
        }
    }
}
