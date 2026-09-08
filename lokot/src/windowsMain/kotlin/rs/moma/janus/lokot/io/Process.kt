package rs.moma.janus.lokot.io

import kotlinx.cinterop.*
import platform.windows.*

@OptIn(ExperimentalForeignApi::class)
class Process(
    private val process: HANDLE?,
    private val thread: HANDLE?,
    private val toChild: HANDLE?,
    private val fromChild: HANDLE?,
) {
    fun write(bytes: ByteArray) = memScoped {
        val wrote = alloc<DWORDVar>()
        var written = 0
        bytes.usePinned { p ->
            while (written < bytes.size) {
                val ok = WriteFile(toChild, p.addressOf(written), (bytes.size - written).convert(), wrote.ptr, null)
                if (ok == 0 || wrote.value.toInt() <= 0) error("the connection closed while sending")
                written += wrote.value.toInt()
            }
        }
    }

    fun readFully(bytes: ByteArray): Boolean = memScoped {
        val got = alloc<DWORDVar>()
        var read = 0
        bytes.usePinned { pinned ->
            while (read < bytes.size) {
                val ok = ReadFile(fromChild, pinned.addressOf(read), (bytes.size - read).convert(), got.ptr, null)
                if (ok == 0 || got.value.toInt() <= 0) return false
                read += got.value.toInt()
            }
        }
        true
    }

    fun close(): Int = memScoped {
        CloseHandle(toChild)
        CloseHandle(fromChild)
        WaitForSingleObject(process, INFINITE)
        val status = alloc<DWORDVar>()
        GetExitCodeProcess(process, status.ptr)
        CloseHandle(thread)
        CloseHandle(process)
        status.value.toInt()
    }
}

@OptIn(ExperimentalForeignApi::class)
fun startProcess(command: List<String>): Process? = memScoped {
    val inheritable = alloc<SECURITY_ATTRIBUTES>().apply {
        nLength = sizeOf<SECURITY_ATTRIBUTES>().convert()
        bInheritHandle = TRUE
        lpSecurityDescriptor = null
    }

    val downRead = alloc<HANDLEVar>()  // the child reads what lokot writes
    val downWrite = alloc<HANDLEVar>()
    val upRead = alloc<HANDLEVar>()    // lokot reads what the child writes
    val upWrite = alloc<HANDLEVar>()
    if (CreatePipe(downRead.ptr, downWrite.ptr, inheritable.ptr, 0u) == 0) return null
    if (CreatePipe(upRead.ptr, upWrite.ptr, inheritable.ptr, 0u) == 0) return null

    // Only the child's ends may be inherited, or the pipes never report their own end.
    SetHandleInformation(downWrite.value, HANDLE_FLAG_INHERIT.convert(), 0u)
    SetHandleInformation(upRead.value, HANDLE_FLAG_INHERIT.convert(), 0u)

    val startup = alloc<STARTUPINFOW>().apply {
        cb = sizeOf<STARTUPINFOW>().convert()
        dwFlags = STARTF_USESTDHANDLES.convert()
        hStdInput = downRead.value
        hStdOutput = upWrite.value
        hStdError = GetStdHandle(STD_ERROR_HANDLE)
    }
    val information = alloc<PROCESS_INFORMATION>()

    val started = CreateProcessW(
        null, quoted(command).wcstr.ptr, null, null, TRUE, 0u, null, null, startup.ptr, information.ptr
    )
    CloseHandle(downRead.value)
    CloseHandle(upWrite.value)
    if (started == 0) {
        CloseHandle(downWrite.value)
        CloseHandle(upRead.value)
        return null
    }

    Process(information.hProcess, information.hThread, downWrite.value, upRead.value)
}

// Windows hands the child one string, so an argument with a space in it has to survive quoting.
private fun quoted(command: List<String>): String = command.joinToString(" ") { argument ->
    if (argument.none { it == ' ' || it == '"' }) argument
    else "\"" + argument.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
