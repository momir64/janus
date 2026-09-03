package rs.moma.privezak.ui.components

import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rs.moma.privezak.ui.theme.*

@Composable
fun PinField(
    state: TextFieldState,
    label: String,
    imeAction: ImeAction? = null,
    onKeyboardAction: (() -> Unit)? = null
) {
    OutlinedSecureTextField(
        state = state,
        label = { Text(label) },
        shape = RoundedCornerShape(8.dp),
        textObfuscationMode = TextObfuscationMode.Hidden,
        keyboardOptions = KeyboardOptions(imeAction = imeAction ?: ImeAction.Unspecified),
        onKeyboardAction = onKeyboardAction?.let { action -> KeyboardActionHandler { action() } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Grey,
            focusedLabelColor = Muted,
            cursorColor = Heading,
            unfocusedBorderColor = DarkGrey,
            unfocusedLabelColor = Muted
        ),
        modifier = Modifier.fillMaxWidth()
    )
}
