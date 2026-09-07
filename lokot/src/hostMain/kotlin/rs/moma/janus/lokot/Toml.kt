package rs.moma.janus.lokot

sealed interface TomlValue

class TomlBoolean(val value: Boolean) : TomlValue
class TomlString(val value: String) : TomlValue
class TomlInteger(val value: Long) : TomlValue

class TomlTable(val entries: Map<String, TomlValue>) : TomlValue {
    val keys: Set<String> get() = entries.keys

    fun table(key: String): TomlTable? = when (val found = entries[key]) {
        null -> null
        is TomlTable -> found
        else -> throw TomlException("'$key' is a value, expected a table")
    }

    fun string(key: String): String? = when (val found = entries[key]) {
        null -> null
        is TomlString -> found.value
        else -> throw TomlException("'$key' should be a string")
    }

    fun integer(key: String): Long? = when (val found = entries[key]) {
        null -> null
        is TomlInteger -> found.value
        else -> throw TomlException("'$key' should be an integer")
    }

    fun unknownKeys(known: Set<String>): Set<String> = keys - known
}

class TomlException(message: String) : Exception(message)

object Toml {
    fun parse(text: String): TomlTable {
        val root = mutableMapOf<String, MutableMap<String, TomlValue>>()
        var current = root.getOrPut("") { mutableMapOf() }

        text.removePrefix("\uFEFF").lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed

            try {
                if (line.startsWith("[")) {
                    if (line.startsWith("[[")) throw TomlException("arrays of tables are not supported yet")
                    val end = line.indexOf(']')
                    if (end < 0) throw TomlException("table header is missing its ']'")
                    Cursor(line, end + 1).expectEnd()
                    current = root.getOrPut(line.substring(1, end).trim()) { mutableMapOf() }
                } else {
                    val cursor = Cursor(line, 0)
                    val key = cursor.readBareKey()
                    cursor.expect('=')
                    val value = cursor.readValue()
                    cursor.expectEnd()
                    if (current.put(key, value) != null) throw TomlException("'$key' is defined twice")
                }
            } catch (failure: TomlException) {
                throw TomlException("line ${index + 1}: ${failure.message}")
            }
        }

        val tree = mutableMapOf<String, TomlValue>()
        root.forEach { (path, entries) ->
            if (path.isEmpty()) {
                tree.putAll(entries)
            } else {
                var target = tree
                val segments = path.split('.')
                segments.dropLast(1).forEach { segment ->
                    val nested = (target[segment] as? TomlTable)?.entries?.toMutableMap() ?: mutableMapOf()
                    target[segment] = TomlTable(nested)
                    target = nested
                }
                target[segments.last()] = TomlTable(entries)
            }
        }
        return TomlTable(tree)
    }

    private class Cursor(private val line: String, private var at: Int) {
        fun readBareKey(): String {
            skipSpace()
            val start = at
            while (at < line.length && (line[at].isLetterOrDigit() || line[at] in "_-.")) at++
            if (at == start) throw TomlException("expected a key")
            return line.substring(start, at)
        }

        fun readValue(): TomlValue {
            skipSpace()
            if (at >= line.length) throw TomlException("expected a value")

            return when {
                line.startsWith("\"\"\"", at) || line.startsWith("'''", at) ->
                    throw TomlException("multi-line strings are not supported yet")

                line[at] == '"' || line[at] == '\'' -> TomlString(readString())
                line[at] == '[' -> throw TomlException("arrays are not supported yet")
                line[at] == '{' -> readInlineTable()
                line.startsWith("true", at) -> {
                    at += 4; TomlBoolean(true)
                }
                line.startsWith("false", at) -> {
                    at += 5; TomlBoolean(false)
                }
                else -> readInteger()
            }
        }

        private fun readString(): String {
            val quote = line[at]
            at++
            val out = StringBuilder()
            while (true) {
                if (at >= line.length) throw TomlException("string is missing its closing quote")
                when (val character = line[at]) {
                    quote -> {
                        at++; return out.toString()
                    }
                    '\\' if quote == '"' -> {
                        at++
                        if (at >= line.length) throw TomlException("string ends in a backslash")
                        out.append(
                            when (val escaped = line[at]) {
                                'n' -> '\n'
                                't' -> '\t'
                                'r' -> '\r'
                                '"' -> '"'
                                '\\' -> '\\'
                                else -> throw TomlException("unknown escape '\\$escaped'")
                            }
                        )
                        at++
                    }
                    else -> {
                        out.append(character); at++
                    }
                }
            }
        }

        private fun readInteger(): TomlInteger {
            val start = at
            if (at < line.length && line[at] in "+-") at++
            while (at < line.length && (line[at].isDigit() || line[at] == '_')) at++
            val text = line.substring(start, at).replace("_", "")
            if (at < line.length && line[at] in ".eE-")
                throw TomlException("only integers are supported, not floats or dates")
            return TomlInteger(text.toLongOrNull() ?: throw TomlException("'$text' is not a value lokot understands"))
        }

        private fun readInlineTable(): TomlTable {
            val entries = mutableMapOf<String, TomlValue>()
            at++ // {
            skipSpace()
            if (at < line.length && line[at] == '}') {
                at++; return TomlTable(entries)
            }

            while (true) {
                val key = readBareKey()
                expect('=')
                if (entries.put(key, readValue()) != null) throw TomlException("'$key' is defined twice")
                skipSpace()
                if (at >= line.length) throw TomlException("inline table is missing its '}'")
                when (line[at]) {
                    ',' -> {
                        at++; skipSpace()
                    }
                    '}' -> {
                        at++; return TomlTable(entries)
                    }
                    else -> throw TomlException("expected ',' or '}' in an inline table")
                }
            }
        }

        fun expect(character: Char) {
            skipSpace()
            if (at >= line.length || line[at] != character) throw TomlException("expected '$character'")
            at++
        }

        fun expectEnd() {
            skipSpace()
            if (at < line.length && line[at] != '#') throw TomlException("unexpected '${line.substring(at)}'")
        }

        private fun skipSpace() {
            while (at < line.length && (line[at] == ' ' || line[at] == '\t')) at++
        }
    }
}
