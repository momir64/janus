package rs.moma.privezak.ui.screens

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import rs.moma.privezak.ui.theme.PrivezakTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.Image
import rs.moma.privezak.ui.theme.Heading
import rs.moma.privezak.ui.theme.Muted
import androidx.compose.ui.Modifier
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import rs.moma.privezak.R

@Composable
fun WelcomeScreen(onContinue: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.weight(1f))
        Image(
            painter = painterResource(R.drawable.keys),
            contentDescription = null,
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
        )
        Spacer(Modifier.weight(1f))

        Text("PRIVEZAK", style = typography.displaySmall, color = Heading)
        Spacer(Modifier.height(12.dp))
        Text(
            "A secure place for your passkeys.\nTo start, you'll need to choose a PIN.",
            style = typography.bodyMedium,
            color = Muted
        )

        Spacer(Modifier.height(32.dp))
        Button(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(8.dp),
            onClick = onContinue
        ) {
            Text("Set up a PIN")
        }
        Spacer(Modifier.height(58.dp))
    }
}

@Preview(showSystemUi = true)
@Composable
private fun WelcomeScreenPreview() {
    PrivezakTheme {
        Surface { WelcomeScreen {} }
    }
}
