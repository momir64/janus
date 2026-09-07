package rs.moma.janus.lokot

import kotlinx.cinterop.*
import platform.windows.*

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
