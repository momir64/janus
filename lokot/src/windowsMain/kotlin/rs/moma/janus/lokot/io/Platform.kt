package rs.moma.janus.lokot.io

import rs.moma.janus.lokot.cli.DEFAULT_CONSOLE
import rs.moma.janus.lokot.cli.ConsoleSize
import kotlinx.cinterop.*
import platform.windows.*
import platform.posix.*

private const val ENABLE_VIRTUAL_TERMINAL_PROCESSING = 0x0004u

private const val ENABLE_VIRTUAL_TERMINAL_INPUT = 0x0200u

@OptIn(ExperimentalForeignApi::class)
fun <T> withRawTerminal(block: () -> T): T = memScoped {
    val input = GetStdHandle(STD_INPUT_HANDLE)
    val output = GetStdHandle(STD_OUTPUT_HANDLE)

    val originalInput = alloc<DWORDVar>()
    val originalOutput = alloc<DWORDVar>()
    val restoreInput = input != INVALID_HANDLE_VALUE && GetConsoleMode(input, originalInput.ptr) != 0
    val restoreOutput = output != INVALID_HANDLE_VALUE && GetConsoleMode(output, originalOutput.ptr) != 0

    if (restoreInput) {
        val quiet = originalInput.value and (ENABLE_ECHO_INPUT.toUInt() or
                ENABLE_LINE_INPUT.toUInt() or ENABLE_PROCESSED_INPUT.toUInt()).inv()
        SetConsoleMode(input, quiet or ENABLE_VIRTUAL_TERMINAL_INPUT)
    }
    if (restoreOutput) SetConsoleMode(output, originalOutput.value or ENABLE_VIRTUAL_TERMINAL_PROCESSING)

    try {
        block()
    } finally {
        if (restoreInput) SetConsoleMode(input, originalInput.value)
        if (restoreOutput) SetConsoleMode(output, originalOutput.value)
    }
}

/** One byte of input, or -1 at the end of the stream. */
@OptIn(ExperimentalForeignApi::class)
fun readRawByte(): Int = memScoped {
    val handle = GetStdHandle(STD_INPUT_HANDLE)
    if (handle == INVALID_HANDLE_VALUE) return@memScoped -1
    val byte = alloc<ByteVar>()
    val got = alloc<DWORDVar>()
    if (ReadFile(handle, byte.ptr, 1u, got.ptr, null) == 0 || got.value.toInt() != 1) -1
    else byte.value.toInt() and 0xFF
}

@OptIn(ExperimentalForeignApi::class)
fun consoleSize(): ConsoleSize = memScoped {
    val handle = GetStdHandle(STD_OUTPUT_HANDLE)
    if (handle == INVALID_HANDLE_VALUE) return@memScoped DEFAULT_CONSOLE
    val info = alloc<CONSOLE_SCREEN_BUFFER_INFO>()
    if (GetConsoleScreenBufferInfo(handle, info.ptr) == 0) return@memScoped DEFAULT_CONSOLE

    val columns = info.srWindow.Right - info.srWindow.Left + 1
    val rows = info.srWindow.Bottom - info.srWindow.Top + 1
    if (columns <= 0 || rows <= 0) DEFAULT_CONSOLE else ConsoleSize(columns, rows)
}

const val CONNECT_NEXT_KEY = "Press Enter, then choose the key you want to add in the Windows Hello dialog."

@OptIn(ExperimentalForeignApi::class)
fun readHidden(prompt: String): String? {
    print(prompt)

    val input = GetStdHandle(STD_INPUT_HANDLE)
    if (input == INVALID_HANDLE_VALUE) return readlnOrNull()

    return memScoped {
        val original = alloc<DWORDVar>()
        val restore = GetConsoleMode(input, original.ptr) != 0

        if (restore) SetConsoleMode(input, original.value and ENABLE_ECHO_INPUT.inv().convert())

        try {
            readlnOrNull()
        } finally {
            if (restore) SetConsoleMode(input, original.value) else println()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
fun flushToDisk(file: CPointer<FILE>) {
    fflush(file)
    _commit(fileno(file))
}

fun replaceFile(temporary: String, path: String): Boolean {
    remove(path)
    return rename(temporary, path) == 0
}

fun secretsRoot(): String = ".lokot-secrets"
fun createDirectory(path: String, mode: Int): Boolean = mkdir(path) == 0 || errno == EEXIST
fun applyMode(path: String, mode: Int) {
}

@OptIn(ExperimentalForeignApi::class)
private fun CPointer<platform.posix.sockaddr_in>.asBytes() = this.reinterpret<ByteVar>()

@OptIn(ExperimentalForeignApi::class)
fun openLoopbackListener(): Long = memScoped<Long> {
    val started = alloc<platform.posix.WSADATA>()
    if (platform.posix.WSAStartup(0x0202u.toUShort(), started.ptr) != 0) return -1

    val listener = platform.posix.socket(platform.posix.AF_INET, platform.posix.SOCK_STREAM, 0)
    if (listener.toLong() < 0) return -1

    val address = alloc<platform.posix.sockaddr_in>()
    memset(address.ptr, 0, sizeOf<platform.posix.sockaddr_in>().convert())
    address.sin_family = platform.posix.AF_INET.convert()
    address.ptr.asBytes().let { bytes -> bytes[4] = 127; bytes[5] = 0; bytes[6] = 0; bytes[7] = 1 }

    val bound = platform.posix.bind(listener, address.ptr.reinterpret(), sizeOf<platform.posix.sockaddr_in>().convert()) == 0
    if (!bound || platform.posix.listen(listener, 4) != 0) {
        platform.posix.closesocket(listener.convert())
        return -1
    }
    listener.toLong()
}

@OptIn(ExperimentalForeignApi::class)
fun listenerPort(listener: Long): Int = memScoped<Int> {
    val address = alloc<platform.posix.sockaddr_in>()
    val size = alloc<IntVar>()
    size.value = sizeOf<platform.posix.sockaddr_in>().convert()
    if (platform.posix.getsockname(listener.convert(), address.ptr.reinterpret(), size.ptr) != 0) return -1

    val bytes = address.ptr.asBytes()
    ((bytes[2].toInt() and 0xFF) shl 8) or (bytes[3].toInt() and 0xFF)
}

@OptIn(ExperimentalForeignApi::class)
fun acceptConnection(listener: Long): Long = platform.posix.accept(listener.convert(), null, null).toLong()

@OptIn(ExperimentalForeignApi::class)
fun receiveBytes(connection: Long, buffer: ByteArray): Int = buffer.usePinned { pinned ->
    platform.posix.recv(connection.convert(), pinned.addressOf(0), buffer.size, 0)
}

@OptIn(ExperimentalForeignApi::class)
fun sendBytes(connection: Long, bytes: ByteArray, offset: Int): Int = bytes.usePinned { pinned ->
    platform.posix.send(connection.convert(), pinned.addressOf(offset), bytes.size - offset, 0)
}

@OptIn(ExperimentalForeignApi::class)
fun closeSocket(handle: Long) {
    platform.posix.closesocket(handle.convert())
}

@OptIn(ExperimentalForeignApi::class)
fun connectLoopback(port: Int): Long = memScoped<Long> {
    val connection = platform.posix.socket(platform.posix.AF_INET, platform.posix.SOCK_STREAM, 0)
    if (connection.toLong() < 0) return -1

    val address = alloc<platform.posix.sockaddr_in>()
    memset(address.ptr, 0, sizeOf<platform.posix.sockaddr_in>().convert())
    address.sin_family = platform.posix.AF_INET.convert()
    address.ptr.asBytes().let { bytes ->
        bytes[2] = (port shr 8).toByte(); bytes[3] = port.toByte()
        bytes[4] = 127; bytes[5] = 0; bytes[6] = 0; bytes[7] = 1
    }

    val joined = platform.posix.connect(
        connection, address.ptr.reinterpret(), sizeOf<platform.posix.sockaddr_in>().convert()
    ) == 0
    if (!joined) {
        platform.posix.closesocket(connection.convert())
        return -1
    }
    connection.toLong()
}

fun browserCommand(address: String): List<String> = listOf("cmd", "/c", "start", "", address)
