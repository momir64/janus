package rs.moma.janus.lokot.io

import rs.moma.janus.lokot.cli.DEFAULT_CONSOLE
import rs.moma.janus.lokot.cli.ConsoleSize
import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
fun <T> withRawTerminal(block: () -> T): T = memScoped {
    val original = alloc<termios>()
    val restore = tcgetattr(STDIN_FILENO, original.ptr) == 0

    if (restore) {
        val raw = alloc<termios>()
        memcpy(raw.ptr, original.ptr, sizeOf<termios>().convert())
        raw.c_lflag = raw.c_lflag and (ICANON or ECHO or ISIG or IEXTEN).inv().convert()
        raw.c_iflag = raw.c_iflag and (IXON or ICRNL or INPCK or ISTRIP).inv().convert()
        raw.c_oflag = raw.c_oflag and OPOST.inv().convert()
        raw.c_cc[VMIN] = 1u   // a read returns as soon as one byte is there
        raw.c_cc[VTIME] = 0u  // and waits forever for it
        tcsetattr(STDIN_FILENO, TCSAFLUSH, raw.ptr)
    }

    try {
        block()
    } finally {
        if (restore) tcsetattr(STDIN_FILENO, TCSAFLUSH, original.ptr)
    }
}

@OptIn(ExperimentalForeignApi::class)
fun readRawByte(): Int = memScoped {
    val byte = alloc<ByteVar>()
    if (read(STDIN_FILENO, byte.ptr, 1u).toInt() != 1) -1 else byte.value.toInt() and 0xFF
}

@OptIn(ExperimentalForeignApi::class)
fun consoleSize(): ConsoleSize = memScoped {
    val size = alloc<winsize>()
    if (ioctl(STDOUT_FILENO, TIOCGWINSZ.convert(), size.ptr) != 0 || size.ws_col.toInt() == 0) DEFAULT_CONSOLE
    else ConsoleSize(size.ws_col.toInt(), size.ws_row.toInt())
}

const val CONNECT_NEXT_KEY = "Connect the key you want to add, then press Enter."

@OptIn(ExperimentalForeignApi::class)
fun readHidden(prompt: String): String? {
    print(prompt)
    fflush(stdout)

    return memScoped {
        val original = alloc<termios>()
        val restore = tcgetattr(STDIN_FILENO, original.ptr) == 0

        if (restore) {
            val quiet = alloc<termios>()
            memcpy(quiet.ptr, original.ptr, sizeOf<termios>().convert())
            quiet.c_lflag = quiet.c_lflag and ECHO.inv().convert()
            tcsetattr(STDIN_FILENO, TCSAFLUSH, quiet.ptr)
        }

        try {
            readlnOrNull()
        } finally {
            if (restore) tcsetattr(STDIN_FILENO, TCSAFLUSH, original.ptr)
            println()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
fun flushToDisk(file: CPointer<FILE>) {
    fflush(file)
    fsync(fileno(file))
}

fun replaceFile(temporary: String, path: String): Boolean = rename(temporary, path) == 0

// `/dev/shm` is the tmpfs linux already has
fun secretsRoot(): String = "/dev/shm/lokot-${getuid()}"

@OptIn(ExperimentalForeignApi::class)
fun createDirectory(path: String, mode: Int): Boolean = mkdir(path, mode.convert()) == 0 || errno == EEXIST

// Directories 0700 (the root gates access), files 0644 (containers read them as their own uids, not the operator).
@OptIn(ExperimentalForeignApi::class)
fun applyMode(path: String, mode: Int) {
    chmod(path, mode.convert())
}

// Loopback sockets, for the browser authenticator. The handle crosses into shared code as a
// Long only because it is a file descriptor here and a SOCKET on Windows.
private const val LOOPBACK = 0x7f000001u

@OptIn(ExperimentalForeignApi::class)
fun openLoopbackListener(): Long = memScoped<Long> {
    val listener = socket(AF_INET, SOCK_STREAM, 0)
    if (listener < 0) return -1

    val address = alloc<sockaddr_in>()
    memset(address.ptr, 0, sizeOf<sockaddr_in>().convert())
    address.sin_family = AF_INET.convert()
    address.sin_addr.s_addr = htonl(LOOPBACK)

    val bound = bind(listener, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) == 0
    if (!bound || listen(listener, 4) != 0) {
        close(listener)
        return -1
    }
    listener.toLong()
}

@OptIn(ExperimentalForeignApi::class)
fun listenerPort(listener: Long): Int = memScoped<Int> {
    val address = alloc<sockaddr_in>()
    val size = alloc<socklen_tVar>()
    size.value = sizeOf<sockaddr_in>().convert()
    if (getsockname(listener.toInt(), address.ptr.reinterpret(), size.ptr) != 0) -1
    else ntohs(address.sin_port).toInt()
}

@OptIn(ExperimentalForeignApi::class)
fun acceptConnection(listener: Long): Long = accept(listener.toInt(), null, null).toLong()

@OptIn(ExperimentalForeignApi::class)
fun receiveBytes(connection: Long, buffer: ByteArray): Int = buffer.usePinned { pinned ->
    recv(connection.toInt(), pinned.addressOf(0), buffer.size.convert(), 0).toInt()
}

@OptIn(ExperimentalForeignApi::class)
fun sendBytes(connection: Long, bytes: ByteArray, offset: Int): Int = bytes.usePinned { pinned ->
    send(connection.toInt(), pinned.addressOf(offset), (bytes.size - offset).convert(), 0).toInt()
}

fun closeSocket(handle: Long) {
    close(handle.toInt())
}

@OptIn(ExperimentalForeignApi::class)
fun connectLoopback(port: Int): Long = memScoped<Long> {
    val connection = socket(AF_INET, SOCK_STREAM, 0)
    if (connection < 0) return -1

    val address = alloc<sockaddr_in>()
    memset(address.ptr, 0, sizeOf<sockaddr_in>().convert())
    address.sin_family = AF_INET.convert()
    address.ptr.reinterpret<ByteVar>().let { bytes ->
        bytes[2] = (port shr 8).toByte(); bytes[3] = port.toByte()
        bytes[4] = 127; bytes[5] = 0; bytes[6] = 0; bytes[7] = 1
    }

    if (connect(connection, address.ptr.reinterpret(), sizeOf<sockaddr_in>().convert()) != 0) {
        close(connection)
        return -1
    }
    connection.toLong()
}

fun browserCommand(address: String): List<String> = listOf("xdg-open", address)
