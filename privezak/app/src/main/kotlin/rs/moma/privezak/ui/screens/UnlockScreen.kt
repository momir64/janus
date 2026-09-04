package rs.moma.privezak.ui.screens

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.imeAnimationSource
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import rs.moma.privezak.ui.components.UnlockContent
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalDensity
import rs.moma.privezak.ui.theme.PrivezakTheme
import androidx.compose.foundation.layout.ime
import androidx.compose.ui.unit.IntOffset
import rs.moma.privezak.ui.theme.Heading
import androidx.compose.ui.unit.Density
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnlockScreen(
    biometricEnabled: Boolean,
    onUnlock: suspend (String) -> Boolean,
    onUnlockWithBiometric: suspend () -> String?
) {
    val focusManager = LocalFocusManager.current

    val lift = with(LocalDensity.current) { 56.dp.roundToPx() }
    val imeFrom = WindowInsets.imeAnimationSource
    val imeTo = WindowInsets.imeAnimationTarget
    val ime = WindowInsets.ime
    fun Density.imeLift(): IntOffset {
        val travel = maxOf(imeFrom.getBottom(this), imeTo.getBottom(this))
        val shown = (ime.getBottom(this).toFloat() / travel.coerceAtLeast(1)).coerceIn(0f, 1f)
        return IntOffset(0, -(lift * (1f - shown)).toInt())
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
        UnlockContent(
            biometricEnabled = biometricEnabled,
            onUnlock = onUnlock,
            onUnlockWithBiometric = onUnlockWithBiometric,
            modifier = Modifier.fillMaxWidth()
        )
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
