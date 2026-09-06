package rs.moma.janus.privezak.provider

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put

private val PRF_PREFIX = "WebAuthn PRF".toByteArray() + 0

private const val PRF = "prf"

// for hybrid connections: https://www.imperialviolet.org/tourofwebauthn/tourofwebauthn.pdf
private const val PRF_ALREADY_HASHED = "prfAlreadyHashed"

internal fun prfSalt(input: String, alreadyHashed: Boolean): ByteArray =
    input.decodeBase64url().let { if (alreadyHashed) it else (PRF_PREFIX + it).sha256() }

private fun extensions(requestJson: String): JsonObject? = runCatching {
    Json.parseToJsonElement(requestJson).jsonObject["extensions"]?.jsonObject
}.getOrNull()

internal fun prfRequested(requestJson: String): Boolean = extensions(requestJson)
    ?.let { it.containsKey(PRF) || it.containsKey(PRF_ALREADY_HASHED) } == true

internal fun prfResults(
    requestJson: String,
    credentialId: String,
    hmacSecret: (ByteArray) -> ByteArray?
): JsonObject? {
    val extensions = extensions(requestJson)
    val alreadyHashed = extensions?.containsKey(PRF_ALREADY_HASHED) == true
    val key = if (alreadyHashed) PRF_ALREADY_HASHED else PRF
    val prf = runCatching { extensions?.get(key)?.jsonObject }.getOrNull()
    val eval = prf?.let { byCredential(it, credentialId) ?: it["eval"]?.jsonObject } ?: return null

    val first = eval["first"]?.jsonPrimitive?.content ?: return null
    val second = eval["second"]?.jsonPrimitive?.content

    val firstOutput = hmacSecret(prfSalt(first, alreadyHashed)) ?: return null
    val secondOutput = second?.let { hmacSecret(prfSalt(it, alreadyHashed)) ?: return null }

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
