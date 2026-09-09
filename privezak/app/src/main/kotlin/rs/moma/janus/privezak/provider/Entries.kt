package rs.moma.janus.privezak.provider

import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.PublicKeyCredentialEntry
import rs.moma.janus.privezak.CredentialActivity
import kotlinx.serialization.json.jsonPrimitive
import rs.moma.janus.privezak.security.Passkey
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.Json
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

const val EXTRA_CREDENTIAL_ID = "rs.moma.janus.privezak.CREDENTIAL_ID"
private const val ENTRY_REQUEST = 10 // 1 is for UNLOCK_REQUEST, 2 is for CREATE_REQUEST

internal fun String.normalised(): String = decodeBase64url().base64url()

internal fun passkeyEntries(
    context: Context,
    options: List<BeginGetPublicKeyCredentialOption>,
    passkeys: List<Passkey>
): List<PublicKeyCredentialEntry> {
    var requestCode = ENTRY_REQUEST
    return options.flatMap { option ->
        val rpId = jsonString(option.requestJson, "rpId")
        val allowed = allowedCredentials(option.requestJson)
        passkeys.filter {
            it.rpId == rpId && (allowed == null || it.id.normalised() in allowed)
        }.map { passkey ->
            val username = passkey.userName
            val intent = assertIntent(context, passkey.id, requestCode++)
            val builder = PublicKeyCredentialEntry.Builder(context, username, intent, option)
            builder.setDisplayName(passkey.displayName).build()
        }
    }
}

private fun assertIntent(ctx: Context, credentialId: String, requestCode: Int): PendingIntent {
    val i = Intent(ctx, CredentialActivity::class.java).putExtra(EXTRA_CREDENTIAL_ID, credentialId)
    val flags = PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    return PendingIntent.getActivity(ctx, requestCode, i, flags)
}

private fun allowedCredentials(requestJson: String): Set<String>? = runCatching {
    Json.parseToJsonElement(requestJson).jsonObject["allowCredentials"]?.jsonArray
        ?.mapNotNull { it.jsonObject["id"]?.jsonPrimitive?.content }
        ?.map { it.normalised() }?.toSet()?.takeIf { it.isNotEmpty() }
}.getOrNull()
