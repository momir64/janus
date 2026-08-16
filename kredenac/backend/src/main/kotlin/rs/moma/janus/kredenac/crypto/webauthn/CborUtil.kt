package rs.moma.janus.kredenac.crypto.webauthn

import rs.moma.janus.kredenac.common.readBigEndianLong

class CborParseException(message: String) : RuntimeException(message)

sealed class CborValue {
    data class Integer(val value: Long) : CborValue()
    class ByteStr(val value: ByteArray) : CborValue()
    data class TextStr(val value: String) : CborValue()
    data class Arr(val value: List<CborValue>) : CborValue()
    data class Map(val value: kotlin.collections.Map<CborValue, CborValue>) : CborValue()
    data class Bool(val value: Boolean) : CborValue()
    object Null : CborValue()

    fun asByteStr() = (this as? ByteStr)?.value
    fun asInteger() = (this as? Integer)?.value

    companion object {
        fun from(bytes: ByteArray, startOffset: Int = 0) = Cursor(bytes, startOffset).readValue() as? Map

        operator fun Map.get(key: String) = value[TextStr(key)]
        operator fun Map.get(key: Long) = value[Integer(key)]
    }

    private class Cursor(private val bytes: ByteArray, startOffset: Int) {
        private var position: Int = startOffset

        fun readValue(): CborValue {
            val initialByte = readByte()
            val majorType = (initialByte.toInt() and 0xFF) shr 5
            val additionalInfo = initialByte.toInt() and 0x1F

            return when (majorType) {
                0 -> Integer(readLength(additionalInfo))
                1 -> Integer(-1L - readLength(additionalInfo))
                2 -> ByteStr(readBytes(readLength(additionalInfo).toInt()))
                3 -> TextStr(String(readBytes(readLength(additionalInfo).toInt()), Charsets.UTF_8))
                4 -> Arr((0 until readLength(additionalInfo)).map { readValue() })
                5 -> {
                    val map = LinkedHashMap<CborValue, CborValue>()
                    repeat(readLength(additionalInfo).toInt()) {
                        val key = readValue()
                        val value = readValue()
                        map[key] = value
                    }
                    Map(map)
                }
                7 -> when (additionalInfo) {
                    20 -> Bool(false)
                    21 -> Bool(true)
                    22 -> Null
                    else -> throw CborParseException("Unsupported simple value: $additionalInfo")
                }
                else -> throw CborParseException("Unsupported major type: $majorType")
            }
        }

        private fun readLength(additionalInfo: Int): Long = when (additionalInfo) {
            in 0..23 -> additionalInfo.toLong()
            24 -> readByte().toLong() and 0xFF
            25 -> readBigEndianLong(2)
            26 -> readBigEndianLong(4)
            27 -> readBigEndianLong(8)
            31 -> throw CborParseException("Indefinite-length encoding is not supported")
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

        private fun readBigEndianLong(count: Int): Long {
            val value = readBigEndianLong(bytes, position, count)
            position += count
            return value
        }
    }
}