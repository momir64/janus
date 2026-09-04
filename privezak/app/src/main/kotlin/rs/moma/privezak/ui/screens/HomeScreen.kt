package rs.moma.privezak.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import rs.moma.privezak.ui.dialogs.ConfirmDialog
import rs.moma.privezak.ui.theme.CardBackground
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import rs.moma.privezak.ui.theme.PrivezakTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.items
import rs.moma.privezak.security.Passkey
import rs.moma.privezak.ui.theme.Heading
import rs.moma.privezak.ui.theme.Muted
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import rs.moma.privezak.R

@Composable
fun HomeScreen(
    passkeys: List<Passkey>,
    onSettings: () -> Unit,
    onScan: () -> Unit,
    onDelete: suspend (String) -> Unit
) {
    var pending by remember { mutableStateOf<Passkey?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Privezak",
                    style = typography.headlineMedium,
                    color = Heading
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(painterResource(R.drawable.ic_settings), contentDescription = "Settings")
                }
            }

            if (passkeys.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "No passkeys yet.",
                        modifier = Modifier.padding(bottom = 32.dp),
                        style = typography.bodyLarge,
                        fontSize = 20.sp,
                        color = Muted
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 128.dp)
                ) {
                    items(passkeys, key = { it.id }) { passkey ->
                        PasskeyCard(passkey) { pending = passkey }
                    }
                }
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

    pending?.let { passkey ->
        ConfirmDialog(
            title = "Removal confirmation",
            text = "This action is permanent. Are you sure you want to delete your passkey?",
            confirmLabel = "Delete",
            onConfirm = {
                pending = null
                scope.launch { onDelete(passkey.id) }
            },
            onDismiss = { pending = null }
        )
    }
}

@Composable
private fun PasskeyCard(passkey: Passkey, onDelete: () -> Unit) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                Text(
                    passkey.rpName.ifEmpty { passkey.rpId },
                    style = typography.titleMedium,
                    color = Heading
                )
                Text(
                    passkey.userName.ifEmpty { passkey.displayName },
                    style = typography.titleSmall,
                    color = Muted
                )
            }
            IconButton(onClick = onDelete) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = "Delete passkey")
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun HomeScreenPreview() {
    PrivezakTheme {
        Surface {
            HomeScreen(
                passkeys = listOf(
                    Passkey("a", "github.com", "GitHub", "dXNlcg==", "test", "Test"),
                    Passkey("b", "google.com", "Google", "dXNlcg==", "test@gmail.com", "Test Test")
                ),
                onSettings = {},
                onScan = {},
                onDelete = {}
            )
        }
    }
}
