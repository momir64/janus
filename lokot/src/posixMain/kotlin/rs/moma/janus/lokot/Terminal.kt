package rs.moma.janus.lokot

import kotlinx.cinterop.*
import platform.posix.*

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
