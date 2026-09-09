package rs.moma.janus.privezak.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    background = Background,
    surface = Background,
    onBackground = Heading,
    onSurface = Heading,
    onSurfaceVariant = Muted,
    primary = DarkGrey,
    onPrimary = Color.White,
    primaryContainer = DarkerGrey,
    onPrimaryContainer = Color.White,
    secondary = DarkGrey,
    onSecondary = Color.White,
    tertiary = Muted,
    onTertiary = Background,
    outline = DarkGrey,
    error = Error
)

@Composable
fun PrivezakTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
