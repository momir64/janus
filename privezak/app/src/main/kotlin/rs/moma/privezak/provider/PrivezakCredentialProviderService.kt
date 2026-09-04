package rs.moma.privezak.provider

import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.GetCredentialException
import rs.moma.privezak.security.PasskeyStore
import rs.moma.privezak.CredentialActivity
import rs.moma.privezak.security.Session
import androidx.credentials.provider.*
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.app.PendingIntent
import android.content.Intent

private const val UNLOCK_REQUEST = 1
private const val CREATE_REQUEST = 2

class PrivezakCredentialProviderService : CredentialProviderService() {
    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>
    ) {
        val passkeys = Session.key()?.let { key ->
            runCatching { PasskeyStore(this, key).load() }.getOrNull()
        }
        if (passkeys == null) {
            val unlock = AuthenticationAction("Unlock Privezak", unlockIntent())
            callback.onResult(BeginGetCredentialResponse(authenticationActions = listOf(unlock)))
            return
        }
        val options = request.beginGetCredentialOptions
            .filterIsInstance<BeginGetPublicKeyCredentialOption>()
        callback.onResult(BeginGetCredentialResponse(passkeyEntries(this, options, passkeys)))
    }

    override fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>
    ) {
        val save = CreateEntry.Builder("Privezak", createIntent()).build()
        callback.onResult(BeginCreateCredentialResponse(listOf(save)))
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>
    ) {
        callback.onResult(null)
    }

    private fun createIntent(): PendingIntent = pendingIntent(CREATE_REQUEST)

    private fun unlockIntent(): PendingIntent = pendingIntent(UNLOCK_REQUEST)

    private fun pendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            this, requestCode,
            Intent(this, CredentialActivity::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
}
