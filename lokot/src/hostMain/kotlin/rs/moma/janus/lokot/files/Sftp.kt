package rs.moma.janus.lokot.files

import rs.moma.janus.lokot.io.startProcess
import rs.moma.janus.lokot.io.Process

class Sftp private constructor(private val process: Process) {
    private var nextId = 1

    fun makeDirectory(path: String, mode: Int): Boolean {
        val id = send(MKDIR) { it.string(path).attributes(mode) }
        return status(id) == OK || list(path) != null
    }

    fun write(path: String, bytes: ByteArray, mode: Int): Boolean {
        val open = send(OPEN) { it.string(path).int(WRITE_ONLY or CREATE or TRUNCATE).attributes(mode) }
        val handle = handle(open) ?: return false
        var written = 0
        while (written < bytes.size) {
            val length = minOf(CHUNK, bytes.size - written)
            val chunk = bytes.copyOfRange(written, written + length)
            val id = send(WRITE) { it.string(handle).long(written.toLong()).string(chunk) }
            if (status(id) != OK) return false
            written += length
        }
        return status(send(CLOSE) { it.string(handle) }) == OK
    }

    fun read(path: String): ByteArray? {
        val handle = handle(send(OPEN) { it.string(path).int(READ_ONLY).attributes(FILE_MODE) }) ?: return null
        val out = mutableListOf<Byte>()
        while (true) {
            val reply = exchange(send(READ) { it.string(handle).long(out.size.toLong()).int(CHUNK) }) ?: break
            if (reply.type != DATA) break // a STATUS here is the end of the file
            out += reply.string().toList()
        }
        status(send(CLOSE) { it.string(handle) })
        return out.toByteArray()
    }

    fun list(path: String): List<String>? {
        val handle = handle(send(OPENDIR) { it.string(path) }) ?: return null
        val names = mutableListOf<String>()
        while (true) {
            val reply = exchange(send(READDIR) { it.string(handle) }) ?: break
            if (reply.type != NAME) break
            repeat(reply.int()) {
                val name = reply.text()
                reply.text()
                reply.attributes()
                if (name != "." && name != "..") names += name
            }
        }
        status(send(CLOSE) { it.string(handle) })
        return names
    }

    fun delete(path: String): Boolean = status(send(REMOVE) { it.string(path) }) == OK

    fun deleteDirectory(path: String): Boolean = status(send(RMDIR) { it.string(path) }) == OK

    fun uid(): Int? {
        val home = exchange(send(REALPATH) { it.string(".") })?.takeIf { it.type == NAME } ?: return null
        home.int()
        val path = home.text()
        val attributes = exchange(send(STAT) { it.string(path) })?.takeIf { it.type == ATTRS } ?: return null
        val flags = attributes.int()
        if (flags and SIZE != 0) attributes.long()
        return if (flags and UIDGID != 0) attributes.int() else null
    }

    fun close() = process.close()

    private fun send(type: Int, body: (Packet) -> Unit = {}): Int {
        val id = nextId++
        val packet = Packet(type)
        packet.int(id)
        body(packet)
        process.write(packet.bytes())
        return id
    }

    private fun receive(): Reader? {
        val length = ByteArray(4)
        if (!process.readFully(length)) return null
        val size = length.readBigEndian(0)
        if (size !in 5..MAX_PACKET) return null
        val payload = ByteArray(size)
        if (!process.readFully(payload)) return null
        return Reader(payload)
    }

    private fun exchange(id: Int): Reader? {
        val reader = receive() ?: return null
        return if (reader.int() == id) reader else null
    }

    private fun status(id: Int): Int = exchange(id)?.takeIf { it.type == STATUS }?.int() ?: FAILURE
    private fun handle(id: Int): ByteArray? = exchange(id)?.takeIf { it.type == HANDLE }?.string()

    private class Packet(private val type: Int) {
        private val out = mutableListOf<Byte>()

        fun bytes(): ByteArray = (out.size + 1).toBigEndian() + byteArrayOf(type.toByte()) + out.toByteArray()
        fun long(value: Long) = apply { (0..7).forEach { out += (value ushr (56 - 8 * it)).toByte() } }
        fun int(value: Int) = apply { (0..3).forEach { out += (value ushr (24 - 8 * it)).toByte() } }
        fun attributes(mode: Int) = apply { int(PERMISSIONS).int(mode) }
        fun string(text: String) = string(text.encodeToByteArray())

        fun string(bytes: ByteArray) = apply {
            int(bytes.size)
            out += bytes.toList()
        }
    }

    private class Reader(private val bytes: ByteArray) {
        val type = bytes[0].toInt() and 0xFF
        private var at = 1

        fun long(): Long = (0..7).fold(0L) { value, _ -> (value shl 8) or (bytes[at++].toLong() and 0xFF) }
        fun int(): Int = bytes.readBigEndian(at).also { at += 4 }
        fun text(): String = string().decodeToString()

        fun string(): ByteArray {
            val length = int()
            return bytes.copyOfRange(at, at + length).also { at += length }
        }

        fun attributes() {
            val flags = int()
            if (flags and SIZE != 0) long()
            if (flags and UIDGID != 0) {
                int(); int()
            }
            if (flags and PERMISSIONS != 0) int()
            if (flags and TIMES != 0) {
                int(); int()
            }
            if (flags and EXTENDED != 0) repeat(int()) {
                string(); string()
            }
        }
    }

    companion object {
        private const val PROTOCOL = 3
        private const val CHUNK = 32 * 1024
        private const val MAX_PACKET = 256 * 1024

        private const val INIT = 1
        private const val VERSION = 2
        private const val OPEN = 3
        private const val CLOSE = 4
        private const val READ = 5
        private const val WRITE = 6
        private const val OPENDIR = 11
        private const val READDIR = 12
        private const val REMOVE = 13
        private const val MKDIR = 14
        private const val RMDIR = 15
        private const val REALPATH = 16
        private const val STAT = 17
        private const val STATUS = 101
        private const val HANDLE = 102
        private const val DATA = 103
        private const val NAME = 104
        private const val ATTRS = 105

        private const val FILE_MODE = 384 // 0600
        private const val READ_ONLY = 0x01
        private const val WRITE_ONLY = 0x02
        private const val CREATE = 0x08
        private const val TRUNCATE = 0x10

        private const val SIZE = 0x01
        private const val UIDGID = 0x02
        private const val PERMISSIONS = 0x04
        private const val TIMES = 0x08
        private const val EXTENDED = -0x80000000

        const val OK = 0
        const val FAILURE = 4

        fun connect(host: String): Sftp? {
            val process = startProcess(listOf("ssh", "-s", host, "sftp")) ?: return null
            val sftp = Sftp(process)

            val hello = Packet(INIT)
            hello.int(PROTOCOL)
            process.write(hello.bytes())

            val reply = sftp.receive() ?: return null
            return if (reply.type == VERSION && reply.int() <= PROTOCOL) sftp else null
        }
    }
}
