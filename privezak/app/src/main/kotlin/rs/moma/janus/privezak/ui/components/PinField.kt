package rs.moma.janus.privezak.ui.components

import androidx.compose.foundation.text.input.TextObfuscationMode.Companion.Visible
import androidx.compose.foundation.text.input.TextObfuscationMode.Companion.Hidden
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import rs.moma.janus.privezak.ui.theme.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import rs.moma.janus.privezak.R

@Composable
fun PinField(
    state: TextFieldState,
    label: String,
    imeAction: ImeAction? = null,
    onKeyboardAction: (() -> Unit)? = null
) {
    var revealed by remember { mutableStateOf(false) }

    OutlinedSecureTextField(
        state = state,
        label = { Text(label) },
        shape = RoundedCornerShape(8.dp),
        textObfuscationMode = if (revealed) Visible else Hidden,
        trailingIcon = {
            IconButton(
                onClick = { revealed = !revealed },
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Icon(
                    painterResource(if (revealed) R.drawable.ic_hide else R.drawable.ic_show),
                    contentDescription = if (revealed) "Hide PIN" else "Show PIN"
                )
            }
        },
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
