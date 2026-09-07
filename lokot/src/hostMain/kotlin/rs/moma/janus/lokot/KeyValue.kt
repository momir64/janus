package rs.moma.janus.lokot

object KeyValue {
    private fun isKeyChar(c: Char) = c.isLetterOrDigit() || c in "_.-"

    fun encode(map: Map<String, String>): ByteArray =
        map.entries.joinToString("\n") { (key, value) ->
            require(key.isNotEmpty() && key.all(::isKeyChar)) {
                "key '$key' can contain only letters, digits, underscore, dot, or dash"
            }; "$key = ${escape(value)}"
        }.encodeToByteArray()

    fun decode(bytes: ByteArray): Map<String, String> = bytes.decodeToString()
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }.associate { line ->
            val separatorIdx = line.indexOf('=')
            require(separatorIdx > 0) { "line is not 'key = value': $line" }
            line.take(separatorIdx).trim() to unescape(line.drop(separatorIdx + 1).trim())
        }

    private fun escape(value: String) = value
        .replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")

    private fun unescape(value: String): String {
        val out = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index]
            if (character != '\\' || index == value.length - 1) {
                out.append(character)
                index++
                continue
            }
            when (val escaped = value[index + 1]) {
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                '\\' -> out.append('\\')
                else -> throw IllegalArgumentException("unknown escape '\\$escaped'")
            }
            index += 2
        }
        return out.toString()
    }
}
