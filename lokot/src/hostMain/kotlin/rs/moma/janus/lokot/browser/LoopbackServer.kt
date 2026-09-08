package rs.moma.janus.lokot.browser

import rs.moma.janus.lokot.io.openLoopbackListener
import rs.moma.janus.lokot.io.acceptConnection
import rs.moma.janus.lokot.io.receiveBytes
import rs.moma.janus.lokot.io.listenerPort
import rs.moma.janus.lokot.io.closeSocket
import rs.moma.janus.lokot.io.sendBytes

private const val MAX_REQUEST = 64 * 1024
private const val CHUNK = 8192

internal class HttpRequest(
    val method: String,
    val path: String,
    val body: String,
    private val connection: Long,
) {
    fun respond(status: Int, contentType: String?, text: String = "") {
        val body = text.encodeToByteArray()
        val head = buildString {
            append("HTTP/1.1 $status ${reason(status)}\r\n")
            append("Connection: close\r\n")
            append("Cache-Control: no-store\r\n")
            contentType?.let { append("Content-Type: $it\r\n") }
            append("Content-Length: ${body.size}\r\n\r\n")
        }.encodeToByteArray()

        writeAll(head)
        writeAll(body)
        closeSocket(connection)
    }

    private fun writeAll(bytes: ByteArray) {
        var sent = 0
        while (sent < bytes.size) {
            val wrote = sendBytes(connection, bytes, sent)
            if (wrote <= 0) return
            sent += wrote
        }
    }

    private fun reason(status: Int) = when (status) {
        200 -> "OK"
        204 -> "No Content"
        400 -> "Bad Request"
        else -> "Not Found"
    }
}

internal class LoopbackServer private constructor(private val listener: Long, val port: Int) {
    fun next(): HttpRequest? {
        while (true) {
            val connection = acceptConnection(listener)
            if (connection < 0) return null
            val request = read(connection)
            if (request != null) return request
            closeSocket(connection)
        }
    }

    fun close() = closeSocket(listener)

    private fun read(connection: Long): HttpRequest? {
        val buffer = ByteArray(CHUNK)
        var text = ""
        var end = -1
        while (end < 0) {
            val got = receiveBytes(connection, buffer)
            if (got <= 0) return null
            text += buffer.decodeToString(0, got)
            end = text.indexOf("\r\n\r\n")
            if (text.length > MAX_REQUEST) return null
        }

        val lines = text.substring(0, end).split("\r\n")
        val start = lines.firstOrNull()?.split(" ") ?: return null
        if (start.size < 2) return null

        val length = lines.firstOrNull { it.startsWith("content-length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toIntOrNull() ?: 0
        var body = text.substring(end + 4)
        while (body.length < length) {
            val got = receiveBytes(connection, buffer)
            if (got <= 0) break
            body += buffer.decodeToString(0, got)
        }

        return HttpRequest(start[0].uppercase(), start[1].substringBefore('?'), body.take(length), connection)
    }

    companion object {
        fun open(): LoopbackServer? {
            val listener = openLoopbackListener()
            if (listener < 0) return null
            val port = listenerPort(listener)
            if (port <= 0) {
                closeSocket(listener)
                return null
            }
            return LoopbackServer(listener, port)
        }
    }
}
