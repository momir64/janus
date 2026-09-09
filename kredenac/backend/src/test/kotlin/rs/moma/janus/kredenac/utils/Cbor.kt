package rs.moma.janus.kredenac.utils

// Minimal CBOR writer, so the tests build their input the way
// an authenticator would rather than asserting against hand-copied hex.
object Cbor {
    fun uint(value: Long): ByteArray = head(0, value)

    fun nint(value: Long): ByteArray = head(1, -1 - value)

    fun bytes(value: ByteArray): ByteArray = head(2, value.size.toLong()) + value

    fun text(value: String): ByteArray {
        val utf8 = value.toByteArray()
        return head(3, utf8.size.toLong()) + utf8
    }

    fun array(vararg items: ByteArray): ByteArray = head(4, items.size.toLong()) + concat(items.toList())

    fun map(vararg entries: Pair<ByteArray, ByteArray>): ByteArray =
        head(5, entries.size.toLong()) + concat(entries.map { it.first + it.second })

    fun bool(value: Boolean): ByteArray = byteArrayOf(((7 shl 5) or if (value) 21 else 20).toByte())

    val nul: ByteArray = byteArrayOf(((7 shl 5) or 22).toByte())

    // Header byte, then the argument in the smallest width CBOR allows for it.
    fun head(major: Int, argument: Long): ByteArray {
        val prefix = major shl 5
        return when {
            argument < 24 -> byteArrayOf((prefix or argument.toInt()).toByte())
            argument <= 0xFF -> byteArrayOf((prefix or 24).toByte(), argument.toByte())
            argument <= 0xFFFF -> byteArrayOf((prefix or 25).toByte()) + bigEndian(argument, 2)
            argument <= 0xFFFFFFFFL -> byteArrayOf((prefix or 26).toByte()) + bigEndian(argument, 4)
            else -> byteArrayOf((prefix or 27).toByte()) + bigEndian(argument, 8)
        }
    }

    private fun concat(parts: List<ByteArray>): ByteArray = parts.fold(ByteArray(0)) { acc, part -> acc + part }

    private fun bigEndian(value: Long, width: Int) = ByteArray(width) { i ->
        (value shr ((width - 1 - i) * 8)).toByte()
    }
}
