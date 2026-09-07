package rs.moma.janus.lokot.editor

import rs.moma.janus.lokot.io.withRawTerminal
import rs.moma.janus.lokot.io.consoleSize
import rs.moma.janus.lokot.cli.readKey
import rs.moma.janus.lokot.cli.Key

class Editor(
    text: String,
    private val check: (String) -> String?,
    private val save: (String) -> String?,
) {
    private val buffer = TextBuffer(text)
    private var message: String? = null
    private var saved = text
    private var left = 0
    private var top = 0

    private val modified: Boolean get() = buffer.text() != saved

    fun run() = withRawTerminal {
        val screen = AnsiScreen(consoleSize())
        screen.enter()
        try {
            while (true) {
                draw(screen)
                when (val key = readKey()) {
                    is Key.EndOfInput -> return@withRawTerminal
                    is Key.Control -> when (key.letter) {
                        's' -> write()
                        'x' -> if (quit(screen)) return@withRawTerminal
                        else -> {}
                    }
                    is Key.Typed -> edit { buffer.insert(key.character) }
                    is Key.Enter -> edit { buffer.splitLine() }
                    is Key.Tab -> edit { repeat(4) { buffer.insert(' ') } }
                    is Key.Backspace -> edit { buffer.backspace() }
                    is Key.Delete -> edit { buffer.delete() }
                    is Key.Left -> buffer.moveLeft()
                    is Key.Right -> buffer.moveRight()
                    is Key.Up -> buffer.moveVertically(-1)
                    is Key.Down -> buffer.moveVertically(1)
                    is Key.PageUp -> buffer.moveVertically(-textHeight(screen))
                    is Key.PageDown -> buffer.moveVertically(textHeight(screen))
                    is Key.Home -> buffer.moveToLineStart()
                    is Key.End -> buffer.moveToLineEnd()
                    else -> {}
                }
            }
        } finally {
            screen.leave()
        }
    }

    private inline fun edit(change: () -> Unit) {
        message = null
        change()
    }

    private fun write(): Boolean {
        val text = buffer.text()
        val reason = check(text) ?: save(text)
        if (reason != null) {
            message = reason
            return false
        }
        saved = text
        message = "written"
        return true
    }

    private fun quit(screen: AnsiScreen): Boolean {
        if (!modified) return true
        check(buffer.text())?.let { reason ->
            return ask(screen, reason, " Q Quit without saving", " R Return to editing", "qr") == 'q'
        }
        return when (ask(screen, "Save modified buffer?", " Y Yes", " N No           ^C Cancel", "ync")) {
            'y' -> write()
            'n' -> true
            else -> false
        }
    }

    private fun ask(screen: AnsiScreen, question: String, first: String, second: String, accepted: String): Char {
        while (true) {
            screen.start()
            paintText(screen)
            screen.row(screen.size.rows - 2, question)
            screen.bar(screen.size.rows - 1, first)
            screen.bar(screen.size.rows, second)
            screen.finish(screen.size.rows - 2, minOf(question.length + 2, screen.size.columns))
            when (val key = readKey()) {
                is Key.EndOfInput, is Key.Escape -> return accepted.last()
                is Key.Control -> if (key.letter == 'c') return accepted.last()
                is Key.Typed -> key.character.lowercaseChar().let { if (it in accepted) return it }
                else -> {}
            }
        }
    }

    private fun textHeight(screen: AnsiScreen) = maxOf(1, screen.size.rows - 2)

    private fun paintText(screen: AnsiScreen) {
        for (offset in 0 until textHeight(screen))
            screen.row(1 + offset, buffer.visible(top + offset, left))
    }

    private fun draw(screen: AnsiScreen) {
        left = left.coerceIn(maxOf(0, buffer.column - screen.size.columns + 1), buffer.column)
        top = top.coerceIn(maxOf(0, buffer.row - textHeight(screen) + 1), buffer.row)

        screen.start()
        paintText(screen)
        screen.row(screen.size.rows - 1, message ?: "")
        screen.bar(screen.size.rows, " ^S Save   ^X Quit")
        screen.finish(1 + (buffer.row - top), 1 + (buffer.column - left))
    }
}
