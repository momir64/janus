package rs.moma.janus.lokot.browser

import kotlin.io.encoding.Base64

private val urlEncoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
private val urlDecoder = Base64.UrlSafe.withPadding(Base64.PaddingOption.PRESENT_OPTIONAL)

internal fun String.fromUrlBase64(): ByteArray? = runCatching { urlDecoder.decode(this) }.getOrNull()
internal fun ByteArray.toUrlBase64(): String = urlEncoder.encode(this)

internal const val MAX_UNLOCK_BODY = 16 * 1024

internal fun jsonField(body: String, name: String): String? =
    Regex("\"$name\"\\s*:\\s*\"([A-Za-z0-9_-]{1,4096})\"").find(body)?.groupValues?.get(1)

internal fun challengeJson(rpId: String, salt: ByteArray, credentialIds: List<ByteArray>, home: String = ""): String =
    """{"rpId":"$rpId","home":"$home","salt":"${salt.toUrlBase64()}","credentialIds":[${
        credentialIds.joinToString(",") { "\"${it.toUrlBase64()}\"" }
    }]}"""

internal fun parseUnlockBody(body: String): Pair<ByteArray, ByteArray>? {
    if (body.length > MAX_UNLOCK_BODY) return null
    val credentialId = jsonField(body, "credentialId")?.fromUrlBase64() ?: return null
    val output = jsonField(body, "output")?.fromUrlBase64() ?: return null
    return credentialId to output
}
