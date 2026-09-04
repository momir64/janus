package rs.moma.privezak.ui.screens

import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.painterResource
import rs.moma.privezak.ui.theme.PrivezakTheme
import androidx.compose.foundation.layout.Row
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import rs.moma.privezak.ui.theme.Heading
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import rs.moma.privezak.R

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = "Back")
        }
        Spacer(Modifier.width(4.dp))
        Text("Settings", style = typography.headlineMedium, color = Heading)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {

    }
}

@Preview(showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    PrivezakTheme {
        Surface { SettingsScreen {} }
    }
}
