package rs.moma.janus.lokot.editor

// Manages actual content of text lines and also the cursor position
class TextBuffer(text: String) {
    private val lines = text.split("\n").map { StringBuilder(it) }.toMutableList()
    var column = 0; private set
    var row = 0; private set

    val lineCount: Int get() = lines.size
    fun text(): String = lines.joinToString("\n")

    fun insert(character: Char) {
        lines[row].insert(column, character)
        column++
    }

    fun splitLine() {
        val rest = lines[row].substring(column)
        lines[row].setLength(column)
        lines.add(row + 1, StringBuilder(rest))
        row++
        column = 0
    }

    fun backspace() {
        if (column > 0) {
            lines[row].deleteAt(column - 1)
            column--
        } else if (row > 0) {
            column = lines[row - 1].length
            lines[row - 1].append(lines[row])
            lines.removeAt(row)
            row--
        }
    }

    fun delete() {
        if (column < lines[row].length) {
            lines[row].deleteAt(column)
        } else if (row < lines.size - 1) {
            lines[row].append(lines[row + 1])
            lines.removeAt(row + 1)
        }
    }

    fun moveLeft() {
        if (column > 0) column--
        else if (row > 0) { row--; column = lines[row].length }
    }

    fun moveRight() {
        if (column < lines[row].length) column++
        else if (row < lines.size - 1) { row++; column = 0 }
    }

    fun moveVertically(by: Int) {
        row = (row + by).coerceIn(0, lines.size - 1)
        column = column.coerceAtMost(lines[row].length)
    }

    fun moveToLineStart() {
        column = 0
    }

    fun moveToLineEnd() {
        column = lines[row].length
    }

    fun visible(index: Int, offset: Int): String =
        if (index >= lines.size || offset >= lines[index].length) "" else lines[index].substring(offset)
}
