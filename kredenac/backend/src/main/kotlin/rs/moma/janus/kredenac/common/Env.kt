package rs.moma.janus.kredenac.common

import kotlin.io.encoding.Base64.PaddingOption.PRESENT_OPTIONAL
import kotlin.io.encoding.Base64
import kotlin.io.path.*

private val dotenv: Map<String, String> by lazy { load(".env") ?: load("../.env") ?: emptyMap() }

private fun load(name: String): Map<String, String>? {
    val file = Path(name)
    if (!file.exists()) return null

    return file.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val separatorIndex = line.indexOf('=')
            if (separatorIndex == -1) return@mapNotNull null
            val key = line.substring(0, separatorIndex).trim()
            var value = line.substring(separatorIndex + 1).trim()
            if (value.length >= 2 && value.first() in "\"'" && value.first() == value.last())
                value = value.substring(1, value.length - 1)
            key to value
        }.toMap()
}

object Env {
    fun get(key: String): String = getOrNull(key) ?: error("Missing environment variable: $key")

    fun getOrNull(key: String): String? = System.getenv(key) ?: dotenv[key]

    fun getBytes(key: String): ByteArray = Base64.withPadding(PRESENT_OPTIONAL).decode(get(key))
}