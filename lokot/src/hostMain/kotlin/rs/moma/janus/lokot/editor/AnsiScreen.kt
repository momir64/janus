package rs.moma.janus.lokot.editor

import rs.moma.janus.lokot.io.consoleSize
import platform.posix.fflush
import platform.posix.stdout


@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
class AnsiScreen {
    private val frame = StringBuilder()

    var size = consoleSize(); private set

    fun enter() = send(ALTERNATE_ON + CLEAR + HIDE_CURSOR)
    fun leave() = send(SHOW_CURSOR + ALTERNATE_OFF)

    fun start() {
        val now = consoleSize()
        frame.setLength(0)
        frame.append(HIDE_CURSOR)
        if (now.columns != size.columns || now.rows != size.rows) frame.append(CLEAR)
        size = now
    }

    private fun widthOf(number: Int) = if (number >= size.rows) size.columns - 1 else size.columns

    fun bar(number: Int, text: String) = place(number, INVERT + text.fit(widthOf(number)) + RESET)
    fun row(number: Int, text: String) = place(number, text.fit(widthOf(number)))

    fun finish(cursorRow: Int, cursorColumn: Int) {
        frame.append(ESC).append(cursorRow).append(';').append(cursorColumn).append('H').append(SHOW_CURSOR)
        send(frame.toString())
    }

    private fun place(number: Int, body: String) {
        frame.append(ESC).append(number).append(";1H").append(body)
    }

    private fun send(text: String) {
        print(text)
        fflush(stdout)
    }

    companion object {
        private const val ESCAPE = 27.toChar()
        const val ESC = "$ESCAPE["
        const val CLEAR = ESC + "2J"
        const val INVERT = ESC + "7m"
        const val RESET = ESC + "0m"
        const val HIDE_CURSOR = "$ESC?25l"
        const val SHOW_CURSOR = "$ESC?25h"
        const val ALTERNATE_ON = "$ESC?1049h"
        const val ALTERNATE_OFF = "$ESC?1049l"
    }
}

fun String.fit(width: Int): String = when {
    width <= 0 -> ""
    length == width -> this
    length < width -> this + " ".repeat(width - length)
    width == 1 -> "…"
    else -> take(width - 1) + "…"
}
