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
            if (restore) SetConsoleMode(input, original.value)
            println()
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
