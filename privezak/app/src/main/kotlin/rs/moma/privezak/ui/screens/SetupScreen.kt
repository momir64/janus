package rs.moma.privezak.ui.screens

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import rs.moma.privezak.security.MIN_PIN_LENGTH
import androidx.compose.ui.res.painterResource
import rs.moma.privezak.ui.components.PinField
import rs.moma.privezak.ui.theme.PrivezakTheme
import androidx.compose.foundation.layout.Box
import androidx.activity.compose.BackHandler
import rs.moma.privezak.ui.utils.SingleToast
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import rs.moma.privezak.R

@Composable
fun SetupScreen(onBack: () -> Unit, onConfirm: suspend (String) -> Unit) {
    var busy by remember { mutableStateOf(false) }
    val verify = rememberTextFieldState()
    val scope = rememberCoroutineScope()
    val pin = rememberTextFieldState()

    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    fun confirm() {
        if (pin.text.length < MIN_PIN_LENGTH) {
            SingleToast.show(context, "PIN must be at least $MIN_PIN_LENGTH characters")
            return
        }
        if (!pin.text.contentEquals(verify.text)) {
            SingleToast.show(context, "PINs do not match")
            return
        }
        busy = true
        scope.launch {
            onConfirm(pin.text.toString())
            busy = false
        }
    }

    BackHandler(onBack = onBack)

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures { focusManager.clearFocus() }
        }
    ) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 12.dp)) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
        }

        Column(
            modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Pick your PIN",
                style = typography.headlineMedium,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))

            PinField(pin, "PIN", ImeAction.Next)
            Spacer(Modifier.height(8.dp))
            PinField(verify, "Verify PIN", ImeAction.Done) { confirm() }

            Spacer(Modifier.height(40.dp))
            Button(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                enabled = !busy && pin.text.isNotEmpty() && verify.text.isNotEmpty(),
                onClick = { confirm() }
            ) {
                Text("Confirm")
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun SetupScreenPreview() {
    PrivezakTheme {
        Surface { SetupScreen({}, {}) }
    }
}
