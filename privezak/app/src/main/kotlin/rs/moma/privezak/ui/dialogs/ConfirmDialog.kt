package rs.moma.privezak.ui.dialogs

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.window.DialogWindowProvider
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.font.FontFamily
import rs.moma.privezak.ui.theme.CardBackground
import androidx.compose.foundation.layout.size
import androidx.compose.ui.platform.LocalView
import rs.moma.privezak.ui.theme.DarkerGrey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import rs.moma.privezak.ui.theme.Error
import rs.moma.privezak.ui.theme.Muted
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = {
            Text(title, fontSize = 24.sp)
            val activityWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
            SideEffect { activityWindow?.setDimAmount(0.75f) }
        },
        text = {
            Text(
                text = text,
                modifier = Modifier.padding(bottom = 6.dp),
                fontFamily = FontFamily.Default,
                fontSize = 16.sp,
                color = Muted
            )
        },
        dismissButton = {
            Button(
                modifier = Modifier.size(100.dp, 42.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DarkerGrey),
                shape = RoundedCornerShape(16),
                onClick = onDismiss
            ) { Text("Cancel", color = Color.White) }
            Spacer(Modifier.width(2.dp))
        },
        confirmButton = {
            Button(
                modifier = Modifier.size(100.dp, 42.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Error),
                shape = RoundedCornerShape(16),
                onClick = onConfirm
            ) { Text(confirmLabel) }
        },
        containerColor = CardBackground,
        shape = RoundedCornerShape(8)
    )
}