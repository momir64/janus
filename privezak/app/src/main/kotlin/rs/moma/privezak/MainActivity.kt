package rs.moma.privezak

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import rs.moma.privezak.ui.components.Navigation
import rs.moma.privezak.ui.theme.PrivezakTheme
import androidx.activity.compose.setContent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import android.graphics.Color
import android.os.Bundle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            PrivezakTheme {
                Navigation()
            }
        }
    }
}
