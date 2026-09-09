package rs.moma.janus.lokot.checks

import rs.moma.janus.lokot.editor.TextBuffer
import rs.moma.janus.lokot.editor.fit
import rs.moma.janus.lokot.cli.readKey
import rs.moma.janus.lokot.cli.Key

private const val EDITOR_BUG = "There's a code bug in the editor, not an environment issue."
internal fun editorChecks(): List<Check> {
    val editor = CheckGroup(EDITOR_BUG)
    val editing = editor.section("editor")
    val keys = editor.section("keys")
    val escape = 27.toChar()

    fun keys(input: String): List<Key> {
        var index = 0
        val bytes = input.encodeToByteArray()
        val read = { if (index >= bytes.size) -1 else (bytes[index++].toInt() and 0xFF) }
        val out = mutableListOf<Key>()
        while (index < bytes.size) out += readKey(read)
        return out
    }

    fun key(input: String) = keys(input).single()
    fun pem() = TextBuffer("-----BEGIN-----\nline two\n-----END-----")

    return listOf(
        keys.holds("arrows decode") {
            keys("$escape[A$escape[B$escape[C$escape[D") == listOf(Key.Up, Key.Down, Key.Right, Key.Left)
        },
        keys.holds("home and end decode in both forms") {
            keys("$escape[H$escape[F${escape}OH${escape}OF") == listOf(Key.Home, Key.End, Key.Home, Key.End)
        },
        keys.holds("the numeric forms decode") {
            keys("$escape[3~$escape[5~$escape[6~$escape[1~") == listOf(Key.Delete, Key.PageUp, Key.PageDown, Key.Home)
        },
        keys.holds("control keys decode") {
            val typed = key(24.toChar().toString())
            typed is Key.Control && typed.letter == 'x'
        },
        keys.holds("printable characters come through") {
            val typed = key("k")
            typed is Key.Typed && typed.character == 'k'
        },
        keys.holds("a pasted non-ascii character survives its utf-8 bytes") {
            val typed = key("ß")
            typed is Key.Typed && typed.character == 'ß'
        },
        keys.holds("both backspace conventions decode") {
            keys(8.toChar().toString() + 127.toChar()) == listOf(Key.Backspace, Key.Backspace)
        },
        keys.holds("enter decodes from cr and from lf") {
            keys("\r\n") == listOf(Key.Enter, Key.Enter)
        },

        editing.equals("rows are cut to the width", "abc…") { "abcdefgh".fit(4) },
        editing.equals("rows are padded to the width", "ab   ") { "ab".fit(5) },
        editing.equals("a row that fits is left alone", "abcd") { "abcd".fit(4) },

        editing.equals("typing inserts at the cursor", "xabc") {
            TextBuffer("abc").apply { insert('x') }.text()
        },
        editing.equals("enter splits a line", "ab\nc") {
            TextBuffer("abc").apply { moveRight(); moveRight(); splitLine() }.text()
        },
        editing.equals("backspace at the start joins onto the line above", "line one\nline two") {
            TextBuffer("line one\n\nline two").apply { moveVertically(1); backspace() }.text()
        },
        editing.holds("the cursor lands at the seam after a join") {
            val buffer = TextBuffer("abc\ndef").apply { moveVertically(1); backspace() }
            buffer.row == 0 && buffer.column == 3 && buffer.text() == "abcdef"
        },
        editing.equals("delete at the end of a line pulls the next one up", "abcdef") {
            TextBuffer("abc\ndef").apply { moveToLineEnd(); delete() }.text()
        },
        editing.holds("moving left off a line goes to the end of the one above") {
            val buffer = TextBuffer("abc\ndef").apply { moveVertically(1); moveLeft() }
            buffer.row == 0 && buffer.column == 3
        },
        editing.holds("vertical movement clamps into a shorter line") {
            val buffer = TextBuffer("ab\nlonger").apply { moveVertically(1); moveToLineEnd(); moveVertically(-1) }
            buffer.row == 0 && buffer.column == 2
        },
        editing.holds("movement stops at both ends") {
            val buffer = TextBuffer("only").apply { moveVertically(-9); moveLeft(); moveVertically(9); moveToLineEnd() }
            buffer.row == 0 && buffer.column == 4 && buffer.lineCount == 1
        },
        editing.holds("a multi-line value round trips untouched") {
            pem().text() == "-----BEGIN-----\nline two\n-----END-----" && pem().lineCount == 3
        },
        editing.equals("a horizontally scrolled line shows its tail", "cdef") {
            TextBuffer("abcdef").visible(0, 2)
        },
        editing.equals("scrolling past the end of a line shows nothing", "") {
            TextBuffer("abc").visible(0, 9)
        },
    )
}

