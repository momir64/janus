package rs.moma.janus.lokot.io

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
class Process(private val pid: Int, private val toChild: Int, private val fromChild: Int) {
    fun write(bytes: ByteArray) {
        var written = 0
        bytes.usePinned { pinned ->
            while (written < bytes.size) {
                val wrote = write(toChild, pinned.addressOf(written), (bytes.size - written).convert()).toInt()
                if (wrote <= 0) error("the connection closed while sending")
                written += wrote
            }
        }
    }

    fun readFully(bytes: ByteArray): Boolean {
        var read = 0
        bytes.usePinned { pinned ->
            while (read < bytes.size) {
                val got = read(fromChild, pinned.addressOf(read), (bytes.size - read).convert()).toInt()
                if (got <= 0) return false
                read += got
            }
        }
        return true
    }

    fun close(): Int = memScoped {
        close(toChild)
        close(fromChild)
        val status = alloc<IntVar>()
        waitpid(pid, status.ptr, 0)
        status.value
    }
}

@OptIn(ExperimentalForeignApi::class)
fun startProcess(command: List<String>): Process? = memScoped {
    val down = allocArray<IntVar>(2) // lokot writes, the child reads
    val up = allocArray<IntVar>(2)   // the child writes, lokot reads
    if (pipe(down) != 0 || pipe(up) != 0) return null

    val pid = fork()
    if (pid < 0) return null
    if (pid == 0) {
        dup2(down[0], STDIN_FILENO)
        dup2(up[1], STDOUT_FILENO)
        close(down[0]); close(down[1]); close(up[0]); close(up[1])

        val argv = allocArray<CPointerVar<ByteVar>>(command.size + 1)
        command.forEachIndexed { index, argument -> argv[index] = argument.cstr.ptr }
        argv[command.size] = null
        execvp(command[0], argv)
        _exit(127) // only reached if exec failed, and the parent sees the pipe close
    }

    close(down[0])
    close(up[1])
    Process(pid, down[1], up[0])
}
