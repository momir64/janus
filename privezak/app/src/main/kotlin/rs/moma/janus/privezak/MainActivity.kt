package rs.moma.janus.privezak

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import android.view.WindowManager.LayoutParams.FLAG_SECURE
import rs.moma.janus.privezak.ui.components.Navigation
import rs.moma.janus.privezak.viewmodels.MainViewModel
import rs.moma.janus.privezak.ui.theme.PrivezakTheme
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.AndroidComposeUiFlags
import rs.moma.janus.privezak.security.Session
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import android.graphics.Color
import android.os.Bundle

class MainActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels()

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        // todo: Works around the keyboard collapsing between text fields in 1.12.0, until next BOM.
        AndroidComposeUiFlags.isOutOfFrameSchedulerForTextInputEventsEnabled = false
        window.setFlags(FLAG_SECURE, FLAG_SECURE)
        setContent {
            PrivezakTheme {
                Navigation(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        Session.isVisible = true
    }

    override fun onStop() {
        super.onStop()
        Session.isVisible = false
        if (!isChangingConfigurations) vm.lock()
    }
}
