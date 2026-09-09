package rs.moma.janus.privezak.ui.components

import androidx.compose.material3.ExposedDropdownMenuAnchorType.Companion.PrimaryNotEditable
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import rs.moma.janus.privezak.ui.theme.DropdownBackground
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import rs.moma.janus.privezak.ui.theme.Heading
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun <T> Dropdown(
    modifier: Modifier,
    values: List<T>,
    selected: T,
    label: String? = null,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    ExposedDropdownMenuBox(expanded, onExpandedChange = { expanded = !expanded }, modifier) {
        OutlinedTextField(
            value = selected.toString().replaceFirstChar { it.uppercase() },
            shape = RoundedCornerShape(8.dp),
            label = { label?.let { Text(it) } },
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(PrimaryNotEditable, true),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            colors = OutlinedTextFieldDefaults.colors(focusedLabelColor = Heading)
        )
        ExposedDropdownMenu(
            expanded,
            onDismissRequest = { expanded = false },
            containerColor = DropdownBackground
        ) {
            values.forEach {
                DropdownMenuItem(
                    text = { Text(it.toString().replaceFirstChar { c -> c.uppercase() }) },
                    onClick = {
                        onSelect(it)
                        expanded = false
                        focusManager.clearFocus()
                    }
                )
            }
        }
    }
}
