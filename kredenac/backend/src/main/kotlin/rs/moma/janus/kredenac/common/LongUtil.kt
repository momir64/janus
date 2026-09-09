package rs.moma.janus.kredenac.common

import java.nio.ByteBuffer

fun readBigEndianLong(bytes: ByteArray, offset: Int, length: Int): Long {
    var result = 0L
    for (i in 0 until length)
        result = (result shl 8) or (bytes[offset + i].toLong() and 0xFF)
    return result
}

fun Long.toByteArray(): ByteArray = ByteBuffer.allocate(8).putLong(this).array()