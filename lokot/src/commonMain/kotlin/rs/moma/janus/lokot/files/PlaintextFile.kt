package rs.moma.janus.lokot.files

/** Parsing and rendering of the plaintext format used for `lokot edit`.*/
object PlaintextFile {
    private val BLOCK = "\"".repeat(3)
    private val INDENT = " ".repeat(4)

    private fun isNameChar(character: Char) = character.isLetterOrDigit() || character in "_.-"

    fun encode(entries: Map<String, String>): ByteArray = render(entries).encodeToByteArray()
    fun decode(bytes: ByteArray): Map<String, String> = parse(bytes.decodeToString())

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

    fun parse(text: String): Map<String, String> {
        val lines = text.split("\n")
        val entries = LinkedHashMap<String, String>()
        var index = 0

        while (index < lines.size) {
            val number = index + 1
            val line = lines[index].trim()
            index++
            if (line.isEmpty() || line.startsWith("#")) continue

            val separator = line.indexOf('=')
            if (separator < 0) throw DocumentException(number, "this is not a 'NAME = value' line")
            val name = line.take(separator).trim()
            if (name.isEmpty()) throw DocumentException(number, "there is no name before the '='")
            if (!name.all(::isNameChar))
                throw DocumentException(number, "'$name' can hold only letters, digits, underscore, dot, or dash")

            var value = line.drop(separator + 1).trim()
            if (value == BLOCK) {
                val block = mutableListOf<String>()
                while (index < lines.size && lines[index].trim() != BLOCK) block += lines[index++]
                if (index >= lines.size) throw DocumentException(number, "this $BLOCK is never closed")
                index++ // the line that closes it
                value = block.joinToString("\n").trimIndent()
            }

            if (entries.put(name, value) != null) throw DocumentException(number, "'$name' is set more than once")
        }
        return entries
    }

    private fun isSimple(value: String) = !value.contains('\n') && value != BLOCK && !value.startsWith("#")
}

class DocumentException(val line: Int, message: String) : Exception(message) {
    val described: String get() = "line $line: $message"
}

class VaultBody(val schema: String, val values: Map<String, String>) {
    fun encode(): ByteArray {
        val schemaBytes = schema.encodeToByteArray()
        return schemaBytes.size.toBigEndian() + schemaBytes + PlaintextFile.encode(values)
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