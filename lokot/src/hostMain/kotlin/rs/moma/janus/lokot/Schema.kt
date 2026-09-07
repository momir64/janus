package rs.moma.janus.lokot

import kotlin.io.encoding.Base64

class Schema(val secrets: Map<String, SecretSpec>) {
    companion object {
        private val NAME = Regex("[A-Z][A-Z0-9_]*")
        private val PLANNED = setOf("certs", "bundles", "deliver")

        fun parse(text: String): Schema {
            val root = Toml.parse(text)

            root.keys.firstOrNull { it in PLANNED }?.let {
                throw SchemaException("[$it] is not supported yet")
            }
            root.unknownKeys(setOf("secrets")).firstOrNull()?.let {
                throw SchemaException("unknown table '[$it]'")
            }

            val secrets = root.table("secrets") ?: throw SchemaException("lokot.toml needs a [secrets] table")

            return Schema(
                secrets.entries.mapValues { (name, declaration) ->
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
            )
        }
    }
}

class SchemaException(message: String) : Exception(message)

sealed interface SecretSpec {
    fun generate(): String?

    class RandomBytes(val bytes: Int, val hex: Boolean) : SecretSpec {
        override fun generate(): String = Crypto.randomBytes(bytes).let {
            if (hex) it.toHex() else Base64.encode(it)
        }
    }

    class RandomChars(val chars: Int) : SecretSpec {
        override fun generate(): String = Crypto.randomBytes(chars)
            .let { bytes -> CharArray(chars) { ALPHABET[bytes[it].toInt() and 63] } }.concatToString()
    }

    // The IANA dynamic range, so a generated port doesn't collide with a registered service.
    object RandomPort : SecretSpec {
        const val LOWEST = 49152
        const val HIGHEST = 65535
        override fun generate(): String {
            val draw = Crypto.randomBytes(2)
            return (LOWEST + (((draw[0].toInt() and 0x3F) shl 8) or (draw[1].toInt() and 0xFF))).toString()
        }
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
