package rs.moma.janus.lokot.schema

import rs.moma.janus.lokot.externals.fromHex
import rs.moma.janus.lokot.externals.Crypto
import rs.moma.janus.lokot.externals.toHex
import kotlin.io.encoding.Base64

class Schema(
    val project: String,
    val secrets: Map<String, SecretSpec>,
    val deliveries: List<Delivery>,
    val compose: List<String>,
) {
    private val written: Set<String> = (deliveries.map { it.secret } + compose).toSet()

    fun check(values: Map<String, String>): String? {
        secrets.keys.firstOrNull { it !in values }?.let { return "$it is declared in $SOURCE but has no value here" }
        values.keys.firstOrNull { it !in secrets }?.let { return "$it is not declared in $SOURCE" }

        values.forEach { (name, value) ->
            val problem = when {
                value.isEmpty() -> "has no value"
                name in written && value.any { it == '\n' || it == '\r' } ->
                    "goes into a file of its own, one line long, so it cannot span lines"
                else -> secrets.getValue(name).check(value)
            }
            problem?.let { return "$name $it" }
        }
        return null
    }

    companion object {
        const val SOURCE = "lokot.toml"
        private val NAME = Regex("[A-Z][A-Z0-9_]*")
        private val PROJECT = Regex("[A-Za-z0-9][A-Za-z0-9 ._-]{0,63}")
        private val PLANNED = setOf("certs", "bundles")

        fun parse(text: String): Schema {
            val root = Toml.parse(text)

            root.keys.firstOrNull { it in PLANNED }?.let {
                throw SchemaException("[$it] is not supported yet")
            }
            root.unknownKeys(setOf("project", "secrets", "deliver", "compose")).firstOrNull()?.let {
                throw SchemaException("unknown table '[$it]'")
            }

            val project = root.string("project") ?: throw SchemaException("lokot.toml needs a project name")
            if (!PROJECT.matches(project))
                throw SchemaException("project must be 1-64 characters: letters, digits, space, dot, dash, underscore")

            val declarations = root.table("secrets") ?: throw SchemaException("lokot.toml needs a [secrets] table")
            val secrets = declarations.entries.mapValues { (name, declaration) ->
                if (!NAME.matches(name))
                    throw SchemaException("'$name' must be upper case and start with a letter")
                if (declaration !is TomlTable)
                    throw SchemaException("'$name' should be a table, like { type = \"random\", bytes = 32 }")
                try {
                    SecretSpec.parse(declaration)
                } catch (failure: SchemaException) {
                    throw SchemaException("$name: ${failure.message}")
                }
            }

            val deliveries = Delivery.parseAll(root.table("deliver"))
            deliveries.firstOrNull { it.secret !in secrets }?.let {
                throw SchemaException("[deliver.${it.service}] names '${it.secret}', which is not in [secrets]")
            }
            deliveries.groupBy { it.path }.values.firstOrNull { it.size > 1 }?.let {
                throw SchemaException("'${it[0].path}' is written twice, by ${it[0].secret} and ${it[1].secret}")
            }

            return Schema(project, secrets, deliveries, compose(root.table("compose"), secrets.keys))
        }

        private fun compose(table: TomlTable?, declared: Set<String>): List<String> {
            if (table == null) return emptyList()
            table.unknownKeys(setOf("env")).firstOrNull()?.let {
                throw SchemaException("'$it' means nothing in [compose], which takes env")
            }
            val names = table.strings("env")
                ?: throw SchemaException("[compose] needs env, a list of names to put in .env")

            names.firstOrNull { it !in declared }?.let {
                throw SchemaException("[compose] names '$it', which is not in [secrets]")
            }
            names.groupingBy { it }.eachCount().entries.firstOrNull { it.value > 1 }?.let {
                throw SchemaException("[compose] names '${it.key}' more than once")
            }
            return names
        }
    }
}

class SchemaException(message: String) : Exception(message)

class Delivery(
    val service: String,
    val secret: String,
    val file: String,
    val prefix: String,
    val onlyAtInit: Boolean,
) {
    val path: String get() = "$service/$file"

    companion object {
        private val SEGMENT = Regex("[a-z0-9][a-z0-9._-]{0,63}")

        fun parseAll(deliver: TomlTable?): List<Delivery> = deliver?.entries.orEmpty().flatMap { (service, group) ->
            if (!SEGMENT.matches(service))
                throw SchemaException("service '$service' must be lower case: letters, digits, dot, dash, underscore")
            if (group !is TomlTable)
                throw SchemaException("'[deliver.$service]' should be a table of secret names")
            try {
                parseGroup(service, group)
            } catch (failure: SchemaException) {
                throw SchemaException("deliver.$service: ${failure.message}")
            }
        }

        private fun parseGroup(service: String, group: TomlTable): List<Delivery> {
            val onlyAtInit = group.boolean("init") == true
            return (group.keys - "init").map { secret ->
                when (val destination = group.entries.getValue(secret)) {
                    is TomlString -> Delivery(service, secret, name(destination.value), "", onlyAtInit)
                    is TomlTable -> {
                        destination.unknownKeys(setOf("file", "prefix")).firstOrNull()?.let {
                            throw SchemaException("'$it' means nothing in a delivery, which takes file and prefix")
                        }
                        val file = destination.string("file") ?: throw SchemaException("$secret needs a file name")
                        Delivery(service, secret, name(file), prefix(destination.string("prefix")), onlyAtInit)
                    }
                    else -> throw SchemaException(
                        "$secret should be a file name, or { file = \"redis.conf\", prefix = \"requirepass \" }"
                    )
                }
            }
        }

        private fun name(file: String): String {
            if (!SEGMENT.matches(file))
                throw SchemaException("'$file' must be lower case: letters, digits, dot, dash, underscore")
            if (file.startsWith(".")) throw SchemaException("'$file' cannot start with a dot")
            return file
        }

        private fun prefix(prefix: String?): String {
            if (prefix != null && prefix.any { it.code < 32 })
                throw SchemaException("a prefix cannot contain a newline or any other control character")
            return prefix.orEmpty()
        }
    }
}

sealed interface SecretSpec {
    fun generate(): String?

    fun check(value: String): String? = null

    class RandomBytes(val bytes: Int, val hex: Boolean) : SecretSpec {
        override fun generate(): String = Crypto.randomBytes(bytes).let {
            if (hex) it.toHex() else Base64.encode(it)
        }

        override fun check(value: String): String? {
            val decoded = try {
                if (hex) value.fromHex() else Base64.decode(value)
            } catch (_: Exception) {
                return "should be $bytes bytes written as ${if (hex) "hex" else "base64"}, and will not decode"
            }
            return if (decoded.size == bytes) null else "should be $bytes bytes, and is ${decoded.size}"
        }
    }

    class RandomChars(val chars: Int) : SecretSpec {
        override fun generate(): String = Crypto.randomBytes(chars)
            .let { bytes -> CharArray(chars) { ALPHABET[bytes[it].toInt() and 63] } }.concatToString()

        override fun check(value: String): String? = when {
            value.length != chars -> "should be $chars characters, and is ${value.length}"
            value.any { it.isWhitespace() || it.code < 32 } -> "has a space or a control character in it"
            else -> null
        }
    }

    // The IANA dynamic range, so a generated port doesn't collide with a registered service.
    object RandomPort : SecretSpec {
        const val LOWEST = 49152
        const val HIGHEST = 65535
        override fun generate(): String {
            val draw = Crypto.randomBytes(2)
            return (LOWEST + (((draw[0].toInt() and 0x3F) shl 8) or (draw[1].toInt() and 0xFF))).toString()
        }

        override fun check(value: String): String? =
            if (value.toIntOrNull() in 1..65535) null else "should be a port number, 1 to 65535"
    }

    class Prompted(val hint: String?) : SecretSpec {
        override fun generate(): String? = null
    }

    companion object {
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

        fun parse(declaration: TomlTable): SecretSpec {
            val type = declaration.string("type") ?: throw SchemaException("needs a type")

            fun reject(known: Set<String>) {
                declaration.unknownKeys(known + "type").firstOrNull()?.let {
                    throw SchemaException("'$it' means nothing for type \"$type\"")
                }
            }

            return when (type) {
                "random" -> {
                    reject(setOf("bytes", "chars", "encoding"))
                    val bytes = declaration.integer("bytes")
                    val chars = declaration.integer("chars")
                    when {
                        bytes != null && chars != null -> throw SchemaException("set bytes or chars, not both")
                        bytes != null -> {
                            if (bytes !in 1..1024) throw SchemaException("bytes must be between 1 and 1024")
                            when (val encoding = declaration.string("encoding") ?: "base64") {
                                "base64" -> RandomBytes(bytes.toInt(), hex = false)
                                "hex" -> RandomBytes(bytes.toInt(), hex = true)
                                else -> throw SchemaException("encoding must be \"base64\" or \"hex\", not \"$encoding\"")
                            }
                        }
                        chars != null -> {
                            if (declaration.string("encoding") != null)
                                throw SchemaException("encoding means nothing with chars, which is already text")
                            if (chars !in 8..256) throw SchemaException("chars must be between 8 and 256")
                            RandomChars(chars.toInt())
                        }
                        else -> throw SchemaException("needs bytes or chars")
                    }
                }
                "port" -> {
                    reject(emptySet())
                    RandomPort
                }
                "prompt" -> {
                    reject(setOf("hint"))
                    Prompted(declaration.string("hint"))
                }
                else -> throw SchemaException("unknown type \"$type\", expected random, port or prompt")
            }
        }
    }
}
