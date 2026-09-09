package rs.moma.janus.privezak.provider

import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.ProviderCreateCredentialRequest
import androidx.credentials.provider.ProviderGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.GetPublicKeyCredentialOption
import rs.moma.janus.privezak.viewmodels.MainViewModel
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.GetCredentialResponse
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import androidx.credentials.PublicKeyCredential
import kotlinx.serialization.json.jsonPrimitive
import rs.moma.janus.privezak.security.Passkey
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.put
import android.content.Context
import android.content.Intent

internal suspend fun registrationResult(
    request: ProviderCreateCredentialRequest,
    vm: MainViewModel
): Intent? {
    val calling = request.callingRequest as? CreatePublicKeyCredentialRequest ?: return null
    val json = Json.parseToJsonElement(calling.requestJson).jsonObject
    val challenge = json["challenge"]?.jsonPrimitive?.content ?: return null
    val rp = json["rp"]?.jsonObject
    val rpId = rp?.get("id")?.jsonPrimitive?.content ?: return null
    val user = json["user"]?.jsonObject ?: return null

    val browserHash = calling.clientDataHash
    val clientData = clientDataJson("webauthn.create", challenge, request.callingAppInfo)
    val clientDataHash = browserHash ?: clientData.sha256()

    val passkey = vm.createPasskey(
        rpId = rpId,
        rpName = rp["name"]?.jsonPrimitive?.content.orEmpty(),
        userHandle = user["id"]?.jsonPrimitive?.content?.decodeBase64url() ?: return null,
        userName = user["name"]?.jsonPrimitive?.content.orEmpty(),
        displayName = user["displayName"]?.jsonPrimitive?.content.orEmpty(),
        attestationChallenge = clientDataHash
    ) ?: return null

    val publicKey = vm.publicKey(passkey.id) ?: return null
    val authenticatorData = authenticatorData(rpId, 0, passkey.id.decodeBase64url(), publicKey)
    val attestation = attestation(vm, passkey.id, authenticatorData, clientDataHash)

    val registration = buildJsonObject {
        put("id", passkey.id.normalised())
        put("rawId", passkey.id.normalised())
        put("type", "public-key")
        put("authenticatorAttachment", "platform")
        put("clientExtensionResults", buildJsonObject {
            if (prfRequested(calling.requestJson)) {
                put("prf", buildJsonObject {
                    put("enabled", true)
                    prfResults(calling.requestJson, passkey.id) { vm.hmacSecret(passkey.id, it) }
                        ?.let { put("results", it) }
                })
            }
        })
        put("response", buildJsonObject {
            put("clientDataJSON", if (browserHash == null) clientData.base64url() else "")
            put("attestationObject", attestation.base64url())
            put("transports", buildJsonArray {
                add("internal")
                add("hybrid")
            })
        })
    }

    val result = Intent()
    val response = CreatePublicKeyCredentialResponse(registration.toString())
    PendingIntentHandler.setCreateCredentialResponse(result, response)
    return result
}

private fun attestation(
    vm: MainViewModel,
    id: String,
    authenticatorData: ByteArray,
    clientDataHash: ByteArray
): ByteArray {
    val chain = vm.certificateChain(id)
    if (chain.size < 2) return noneAttestation(authenticatorData)
    val signature = vm.sign(id, authenticatorData + clientDataHash)
        ?: return noneAttestation(authenticatorData)
    return androidKeyAttestation(authenticatorData, signature, chain)
}

internal suspend fun assertionResult(
    request: ProviderGetCredentialRequest,
    credentialId: String,
    vm: MainViewModel
): Intent? {
    val option = request.credentialOptions
        .filterIsInstance<GetPublicKeyCredentialOption>()
        .firstOrNull() ?: return null
    val passkey = vm.passkeys.value.find { it.id == credentialId } ?: return null
    val challenge = jsonString(option.requestJson, "challenge") ?: return null

    val clientData = clientDataJson("webauthn.get", challenge, request.callingAppInfo)
    val clientDataHash = option.clientDataHash ?: clientData.sha256()
    val signCount = vm.recordUse(credentialId) ?: return null
    val authenticatorData = authenticatorData(passkey.rpId, signCount)
    val reported = if (option.clientDataHash == null) clientData.base64url() else ""
    val signature = vm.sign(credentialId, authenticatorData + clientDataHash) ?: return null
    val prf = prfResults(option.requestJson, credentialId) { vm.hmacSecret(credentialId, it) }

    val assertion = buildJsonObject {
        put("id", credentialId.normalised())
        put("rawId", credentialId.normalised())
        put("type", "public-key")
        put("authenticatorAttachment", "platform")
        put("clientExtensionResults", buildJsonObject {
            prf?.let { put("prf", buildJsonObject { put("results", it) }) }
        })
        put("response", buildJsonObject {
            put("clientDataJSON", reported)
            put("authenticatorData", authenticatorData.base64url())
            put("signature", signature.base64url())
            put("userHandle", passkey.userHandle.decodeBase64url().base64url())
        })
    }

    val result = Intent()
    val response = GetCredentialResponse(PublicKeyCredential(assertion.toString()))
    PendingIntentHandler.setGetCredentialResponse(result, response)
    return result
}

internal fun entriesResult(
    context: Context,
    request: BeginGetCredentialRequest?,
    passkeys: List<Passkey>
): Intent {
    val options = request?.beginGetCredentialOptions.orEmpty()
        .filterIsInstance<BeginGetPublicKeyCredentialOption>()
    val entries = passkeyEntries(context, options, passkeys)
    val result = Intent()
    PendingIntentHandler.setBeginGetCredentialResponse(result, BeginGetCredentialResponse(entries))
    return result
}

private fun clientDataJson(type: String, challenge: String, calling: CallingAppInfo): ByteArray {
    val origin = apkKeyHashOrigin(calling.signingInfo.apkContentsSigners.first().toByteArray())
    return buildJsonObject {
        put("type", type)
        put("challenge", challenge)
        put("origin", origin)
        put("androidPackageName", calling.packageName)
    }.toString().toByteArray()
}
