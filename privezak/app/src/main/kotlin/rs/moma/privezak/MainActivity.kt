package rs.moma.privezak

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import rs.moma.privezak.ui.components.Navigation
import rs.moma.privezak.viewmodels.MainViewModel
import rs.moma.privezak.ui.theme.PrivezakTheme
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import android.graphics.Color
import android.os.Bundle

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        window.setFlags(FLAG_SECURE, FLAG_SECURE)
        setContent {
            PrivezakTheme {
                Navigation(vm)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.lock()
    }
}
