package rs.moma.janus.lokot.files

import rs.moma.janus.lokot.externals.toChars
import rs.moma.janus.lokot.externals.wipe

internal object PlaintextFile {
    private const val BLOCK = "\"\"\""
    private val INDENT = " ".repeat(4)

    private fun isNameChar(character: Char) = character.isLetterOrDigit() || character in "_.-"

    fun encode(entries: Map<String, String>): ByteArray = render(entries).encodeToByteArray()

    fun decodeText(bytes: ByteArray): Map<String, String> = parse(bytes.decodeToString())

    fun decode(bytes: ByteArray): Map<String, CharArray> {
        val chars = bytes.toChars()
        try {
            return parse(chars)
        } finally {
            chars.wipe()
        }
    }

    fun render(entries: Map<String, String>): String {
        val width = entries.keys.maxOfOrNull { it.length } ?: 0
        return entries.entries.joinToString("\n") { (name, value) ->
            require(name.isNotEmpty() && name.all(::isNameChar)) {
                "name '$name' can contain only letters, digits, underscore, dot, or dash"
            }
            require(value.lineSequence().none { it.trim() == BLOCK }) {
                "the value of '$name' has a line that would close its own block"
            }
            val label = name.padEnd(width)
            if (isSimple(value)) "$label = $value"
            else "$label = $BLOCK\n${value.prependIndent(INDENT)}\n$INDENT$BLOCK"
        }
    }

    fun parse(text: String): Map<String, String> = parse(text.toCharArray()).mapValues { it.value.concatToString() }

    fun parse(chars: CharArray): Map<String, CharArray> {
        val entries = LinkedHashMap<String, CharArray>()
        val lines = lines(chars)
        var index = 0

        while (index < lines.size) {
            val number = index + 1
            val line = lines[index].trim(chars)
            index++
            if (line.isEmpty || chars[line.start] == '#') continue

            val separator = line.indexOf(chars, '=')
            if (separator < 0) throw DocumentException(number, "this is not a 'NAME = value' line")

            val name = chars.concatToString(line.start, separator).trim()
            if (name.isEmpty()) throw DocumentException(number, "there is no name before the '='")
            if (!name.all(::isNameChar))
                throw DocumentException(number, "'$name' can hold only letters, digits, underscore, dot, or dash")

            val rest = Span(separator + 1, line.end).trim(chars)
            val value = if (!rest.holds(chars, BLOCK)) rest.chars(chars) else {
                val block = mutableListOf<Span>()
                while (index < lines.size && !lines[index].trim(chars).holds(chars, BLOCK)) block += lines[index++]
                if (index >= lines.size) throw DocumentException(number, "this $BLOCK is never closed")
                index++ // the line that closes it
                undent(chars, block)
            }

            if (entries.put(name, value) != null) throw DocumentException(number, "'$name' is set more than once")
        }
        return entries
    }

    private fun isSimple(value: String) = !value.contains('\n') && value != BLOCK && !value.startsWith("#")

    private fun lines(chars: CharArray): List<Span> {
        val lines = mutableListOf<Span>()
        var start = 0
        chars.forEachIndexed { at, character ->
            if (character == '\n') {
                lines += Span(start, at)
                start = at + 1
            }
        }
        lines += Span(start, chars.size)
        return lines
    }

    private fun undent(chars: CharArray, block: List<Span>): CharArray {
        val blank = { line: Span -> line.trim(chars).isEmpty }
        val body = block.dropWhile(blank).dropLastWhile(blank)
        if (body.isEmpty()) return CharArray(0)

        val indent = body.filterNot(blank).minOf { line -> line.trim(chars).start - line.start }
        val kept = body.map { line -> if (blank(line)) Span(line.end, line.end) else Span(line.start + indent, line.end) }

        val value = CharArray(kept.sumOf { it.length } + kept.size - 1)
        var at = 0
        kept.forEachIndexed { index, line ->
            if (index > 0) value[at++] = '\n'
            chars.copyInto(value, at, line.start, line.end)
            at += line.length
        }
        return value
    }
}

private class Span(val start: Int, val end: Int) {
    val length get() = end - start
    val isEmpty get() = end <= start

    fun trim(chars: CharArray): Span {
        var first = start
        var last = end
        while (first < last && chars[first].isWhitespace()) first++
        while (last > first && chars[last - 1].isWhitespace()) last--
        return Span(first, last)
    }

    fun indexOf(chars: CharArray, character: Char): Int {
        for (at in start until end) if (chars[at] == character) return at
        return -1
    }

    fun holds(chars: CharArray, text: String): Boolean =
        length == text.length && text.indices.all { chars[start + it] == text[it] }

    fun chars(chars: CharArray): CharArray = chars.copyOfRange(start, end)
}

internal class DocumentException(val line: Int, message: String) : Exception(message) {
    val described: String get() = "line $line: $message"
}

internal class VaultBody(val schema: String, val values: Map<String, CharArray>) {
    fun encode(): ByteArray {
        val schemaBytes = schema.encodeToByteArray()
        return schemaBytes.size.toBigEndian() + schemaBytes +
                PlaintextFile.encode(values.mapValues { it.value.concatToString() })
    }

    companion object {
        private const val LENGTH_SIZE = 4

        fun decode(bytes: ByteArray): VaultBody {
            require(bytes.size >= LENGTH_SIZE) { "the body is too short to hold a schema" }
            val length = bytes.readBigEndian(0)
            require(length in 0..(bytes.size - LENGTH_SIZE)) { "the body's schema length is out of range" }
            val end = LENGTH_SIZE + length
            return VaultBody(
                schema = bytes.decodeToString(LENGTH_SIZE, end),
                values = PlaintextFile.decode(bytes.copyOfRange(end, bytes.size)),
            )
        }
    }
}