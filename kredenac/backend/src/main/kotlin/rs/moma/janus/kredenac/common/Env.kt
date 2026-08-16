package rs.moma.janus.kredenac.common

import kotlin.io.encoding.Base64.PaddingOption.PRESENT_OPTIONAL
import kotlin.io.encoding.Base64
import kotlin.io.path.*

private val dotenv: Map<String, String> by lazy { loadDotenv() }

private fun loadDotenv(): Map<String, String> {
    val file = Path(".env")
    if (!file.exists()) return emptyMap()

    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex == -1) return@mapNotNull null
            val key = line.substring(0, separatorIndex).trim()
            val value = line.substring(separatorIndex + 1).trim()
            key to value
        }.toMap()
}

object Env {
    fun get(key: String): String = System.getenv(key) ?: dotenv[key] ?: error("Missing environment variable: $key")

    fun getBytes(key: String): ByteArray = Base64.withPadding(PRESENT_OPTIONAL).decode(get(key))
}