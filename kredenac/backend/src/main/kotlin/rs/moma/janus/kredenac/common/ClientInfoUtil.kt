package rs.moma.janus.kredenac.common

class ClientInfo(
    val ip: String?,
    val location: String?,
    val browser: String?,
    val device: String?,
    val timezone: String?
)

private val BROWSERS = linkedMapOf(
    "SamsungBrowser/" to "Samsung Internet",
    "Edg/" to "Microsoft Edge",
    "OPR/" to "Opera",
    "Firefox/" to null,
    "Chrome/" to null,
    "Safari/" to null
)

private val DEVICES = linkedMapOf(
    "Android" to null,
    "iPhone" to null,
    "iPad" to null,
    "Windows NT" to "Windows",
    "Mac OS X" to "macOS",
    "CrOS" to "ChromeOS",
    "Linux" to null
)

fun clientInfo(userAgent: String?, ip: String?, location: String?, timezone: String?): ClientInfo {
    val agent = userAgent.orEmpty()
    return ClientInfo(ip, location, agent.match(BROWSERS), agent.match(DEVICES), timezone)
}

private fun String.match(tokens: Map<String, String?>): String? =
    tokens.entries.firstOrNull { contains(it.key) }?.let { it.value ?: it.key.removeSuffix("/") }
