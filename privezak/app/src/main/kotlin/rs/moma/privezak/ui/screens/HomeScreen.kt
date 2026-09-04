package rs.moma.privezak.ui.screens

import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import rs.moma.privezak.ui.theme.PrivezakTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import rs.moma.privezak.ui.theme.Heading
import rs.moma.privezak.ui.theme.Muted
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import rs.moma.privezak.R

@Composable
fun HomeScreen(onSettings: () -> Unit, onScan: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Privezak", style = typography.headlineMedium, color = Heading)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
            }
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "No passkeys yet.",
                    style = typography.bodyLarge,
                    fontSize = 20.sp,
                    color = Muted
                )
            }
        }

        FloatingActionButton(
            onClick = onScan,
            modifier = Modifier.align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 32.dp)
                .size(70.dp)
        ) {
            Icon(
                painterResource(R.drawable.ic_qr),
                contentDescription = "Scan a QR code",
                modifier = Modifier.size(46.dp)
            )
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    PrivezakTheme {
        Surface { HomeScreen({}, {}) }
    }
}
