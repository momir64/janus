package rs.moma.privezak.ui.components

import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.padding
import rs.moma.privezak.viewmodels.MainViewModel
import rs.moma.privezak.ui.screens.LoginScreen
import androidx.compose.foundation.layout.Box
import rs.moma.privezak.ui.screens.HomeScreen
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.runtime.*

@Composable
fun Navigation(vm: MainViewModel) {
    val activity = LocalActivity.current as FragmentActivity
    val isLoggedIn by vm.isLoggedIn.collectAsState()

    Scaffold(Modifier.fillMaxSize()) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            if (isLoggedIn != true)
                LoginScreen()
            else
                HomeScreen()
        }
    }
}