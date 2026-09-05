package rs.moma.privezak.provider

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put

private val PRF_PREFIX = "WebAuthn PRF".toByteArray() + 0

internal fun prfSalt(input: ByteArray): ByteArray = (PRF_PREFIX + input).sha256()

private fun extensions(requestJson: String): JsonObject? = runCatching {
    Json.parseToJsonElement(requestJson).jsonObject["extensions"]?.jsonObject
}.getOrNull()

internal fun prfRequested(requestJson: String): Boolean =
    extensions(requestJson)?.containsKey("prf") == true

internal fun prfResults(
    requestJson: String,
    credentialId: String,
    hmacSecret: (ByteArray) -> ByteArray?
): JsonObject? {
    val prf = runCatching { extensions(requestJson)?.get("prf")?.jsonObject }.getOrNull()
    val eval = prf?.let { byCredential(it, credentialId) ?: it["eval"]?.jsonObject } ?: return null

    val first = eval["first"]?.jsonPrimitive?.content ?: return null
    val second = eval["second"]?.jsonPrimitive?.content

    val firstOutput = hmacSecret(prfSalt(first.decodeBase64url())) ?: return null
    val secondOutput = second?.let { hmacSecret(prfSalt(it.decodeBase64url())) ?: return null }

    return buildJsonObject {
        put("first", firstOutput.base64url())
        secondOutput?.let { put("second", it.base64url()) }
    }
}

private fun byCredential(prf: JsonObject, credentialId: String): JsonObject? {
    val id = credentialId.normalised()
    return prf["evalByCredential"]?.jsonObject?.entries
        ?.firstOrNull { runCatching { it.key.normalised() }.getOrNull() == id }
        ?.value?.jsonObject
}
