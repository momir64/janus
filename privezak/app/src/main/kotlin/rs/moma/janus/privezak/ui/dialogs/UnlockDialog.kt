package rs.moma.janus.privezak.ui.dialogs

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.typography
import rs.moma.janus.privezak.ui.components.UnlockContent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import rs.moma.janus.privezak.ui.theme.CardBackground
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import rs.moma.janus.privezak.ui.theme.Heading
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*

@Composable
fun UnlockDialog(
    onUnlockWithBiometric: suspend () -> String?,
    onUnlock: suspend (String) -> Boolean,
    biometricEnabled: Boolean,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                onClick = onDismiss,
                indication = null
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .imePadding()
                .padding(24.dp)
                .pointerInput(Unit) { detectTapGestures { } },
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = CardBackground)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("PRIVEZAK", style = typography.headlineMedium, color = Heading)
                Spacer(Modifier.height(20.dp))
                UnlockContent(
                    biometricEnabled = biometricEnabled,
                    onUnlock = onUnlock,
                    onUnlockWithBiometric = onUnlockWithBiometric,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
