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
fun createDirectory(path: String): Boolean = mkdir(path, S_IRWXU.convert()) == 0 || errno == EEXIST

// Directories 0700 (the root gates access), files 0644 (containers read them as their own uids, not the operator).
@OptIn(ExperimentalForeignApi::class)
fun restrictToOwner(path: String, directory: Boolean) {
    chmod(path, (if (directory) S_IRWXU else S_IRUSR or S_IWUSR or S_IRGRP or S_IROTH).convert())
}
