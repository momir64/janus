package rs.moma.janus.lokot.cli

import rs.moma.janus.lokot.io.readRawByte

class ConsoleSize(val columns: Int, val rows: Int)

val DEFAULT_CONSOLE = ConsoleSize(80, 24)

sealed interface Key {
    class Typed(val character: Char) : Key
    class Control(val letter: Char) : Key

    data object Up : Key
    data object Down : Key
    data object Left : Key
    data object Right : Key
    data object Home : Key
    data object End : Key
    data object PageUp : Key
    data object PageDown : Key
    data object Delete : Key
    data object Backspace : Key
    data object Enter : Key
    data object Escape : Key
    data object Tab : Key
    data object Unknown : Key
    data object EndOfInput : Key
}

fun readKey(nextByte: () -> Int = ::readRawByte): Key {
    return when (val byte = nextByte()) {
        -1 -> Key.EndOfInput
        13, 10 -> Key.Enter
        9 -> Key.Tab
        127, 8 -> Key.Backspace
        27 -> readEscape(nextByte)
        in 1..26 -> Key.Control('a' + (byte - 1))
        in 32..126 -> Key.Typed(byte.toChar())
        // pasted "ß" or "→" survives instead of arriving as two pieces of nonsense.
        in 0xC2..0xF4 -> readUtf8(byte, nextByte)
        else -> Key.Unknown
    }
}

private fun readEscape(nextByte: () -> Int): Key {
    val introducer = nextByte()
    if (introducer != '['.code && introducer != 'O'.code) return Key.Escape

    return when (val final = nextByte()) {
        'A'.code -> Key.Up
        'B'.code -> Key.Down
        'C'.code -> Key.Right
        'D'.code -> Key.Left
        'H'.code -> Key.Home
        'F'.code -> Key.End
        in '0'.code..'9'.code -> when (readParameter(final, nextByte)) {
            1, 7 -> Key.Home
            3 -> Key.Delete
            4, 8 -> Key.End
            5 -> Key.PageUp
            6 -> Key.PageDown
            else -> Key.Unknown
        }
        else -> Key.Unknown
    }
}

// Collects the digits of a "\e[<n>~" sequence
private fun readParameter(first: Int, nextByte: () -> Int): Int {
    var value = first - '0'.code
    while (true) {
        when (val byte = nextByte()) {
            in '0'.code..'9'.code -> value = value * 10 + (byte - '0'.code)
            else -> return value // '~', or whatever else ended it
        }
    }
}

private fun readUtf8(first: Int, nextByte: () -> Int): Key {
    val continuations = when {
        first < 0xE0 -> 1
        first < 0xF0 -> 2
        else -> 3
    }
    val bytes = ByteArray(continuations + 1)
    bytes[0] = first.toByte()
    for (index in 1..continuations) {
        val byte = nextByte()
        if (byte !in 0x80..0xBF) return Key.Unknown
        bytes[index] = byte.toByte()
    }
    val decoded = bytes.decodeToString()
    return if (decoded.length == 1) Key.Typed(decoded[0]) else Key.Unknown
}
