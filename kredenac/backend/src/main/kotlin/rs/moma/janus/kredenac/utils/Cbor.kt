package rs.moma.janus.kredenac.utils

sealed class CborValue {
    data class UInt(val value: Long) : CborValue()
    data class NInt(val value: Long) : CborValue()
    class ByteStr(val value: ByteArray) : CborValue()
    data class TextStr(val value: String) : CborValue()
    data class Arr(val value: List<CborValue>) : CborValue()
    data class Map(val value: kotlin.collections.Map<CborValue, CborValue>) : CborValue()
    data class Bool(val value: Boolean) : CborValue()
    object Null : CborValue()
}

class CborParseException(message: String) : RuntimeException(message)

class CborDecoder(private val bytes: ByteArray, startOffset: Int = 0) {
    var position: Int = startOffset
        private set

    fun decode(): CborValue = readValue()

    private fun readValue(): CborValue {
        val initialByte = readByte()
        val majorType = (initialByte.toInt() and 0xFF) shr 5
        val additionalInfo = initialByte.toInt() and 0x1F

        return when (majorType) {
            0 -> CborValue.UInt(readLength(additionalInfo))
            1 -> CborValue.NInt(-1L - readLength(additionalInfo))
            2 -> CborValue.ByteStr(readBytes(readLength(additionalInfo).toInt()))
            3 -> CborValue.TextStr(String(readBytes(readLength(additionalInfo).toInt()), Charsets.UTF_8))
            4 -> {
                val count = readLength(additionalInfo).toInt()
                CborValue.Arr((0 until count).map { readValue() })
            }
            5 -> {
                val count = readLength(additionalInfo).toInt()
                val map = LinkedHashMap<CborValue, CborValue>()
                repeat(count) {
                    val key = readValue()
                    val value = readValue()
                    map[key] = value
                }
                CborValue.Map(map)
            }
            7 -> when (additionalInfo) {
                20 -> CborValue.Bool(false)
                21 -> CborValue.Bool(true)
                22 -> CborValue.Null
                else -> throw CborParseException("Unsupported simple value: $additionalInfo")
            }
            else -> throw CborParseException("Unsupported major type: $majorType")
        }
    }

    private fun readLength(additionalInfo: Int): Long = when (additionalInfo) {
        in 0..23 -> additionalInfo.toLong()
        24 -> readByte().toLong() and 0xFF
        25 -> readBytes(2).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        26 -> readBytes(4).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        27 -> readBytes(8).fold(0L) { acc, b -> (acc shl 8) or (b.toLong() and 0xFF) }
        else -> throw CborParseException("Unsupported additional info: $additionalInfo")
    }

    private fun readByte(): Byte {
        if (position >= bytes.size) throw CborParseException("Unexpected end of CBOR input")
        return bytes[position++]
    }

    private fun readBytes(count: Int): ByteArray {
        if (position + count > bytes.size) throw CborParseException("Unexpected end of CBOR input")
        val slice = bytes.copyOfRange(position, position + count)
        position += count
        return slice
    }
}

fun CborValue.Map.textStr(key: String): CborValue? = value[CborValue.TextStr(key)]

fun CborValue.Map.byIntKey(key: Long): CborValue? {
    val wrapped: CborValue = if (key >= 0) CborValue.UInt(key) else CborValue.NInt(key)
    return value[wrapped]
}
