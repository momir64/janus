package rs.moma.janus.lokot.files

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
object Files {
    fun exists(path: String): Boolean {
        val file = fopen(path, "rb") ?: return false
        fclose(file)
        return true
    }

    fun readBytes(path: String): ByteArray? {
        val file = fopen(path, "rb") ?: return null
        try {
            val size = memScoped {
                val info = alloc<stat>()
                if (fstat(fileno(file), info.ptr) != 0) error("fstat failed for $path")
                info.st_size.convert<Int>()
            }
            if (size == 0) return ByteArray(0)
            return ByteArray(size).also { bytes ->
                val read = bytes.usePinned { fread(it.addressOf(0), 1u, size.toULong(), file) }
                if (read.toInt() != size) error("read $read of $size bytes from $path")
            }
        } finally {
            fclose(file)
        }
    }

    fun readText(path: String): String? = readBytes(path)?.decodeToString()

    fun writeBytes(path: String, bytes: ByteArray) {
        val temporary = "$path.tmp"
        val file = fopen(temporary, "wb") ?: error("cannot write $temporary")
        try {
            if (bytes.isNotEmpty()) {
                val written = bytes.usePinned { fwrite(it.addressOf(0), 1u, bytes.size.convert(), file) }
                if (written.toInt() != bytes.size) error("wrote $written of ${bytes.size} bytes to $temporary")
            }
        } finally {
            fclose(file)
        }
        remove(path)
        if (rename(temporary, path) != 0) error("cannot move $temporary into place")
    }

    fun writeText(path: String, text: String) = writeBytes(path, text.encodeToByteArray())
}
