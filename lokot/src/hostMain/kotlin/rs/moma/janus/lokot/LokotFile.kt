package rs.moma.janus.lokot

import rs.moma.janus.lokot.Crypto.randomBytes
import rs.moma.janus.lokot.Crypto.NONCE_SIZE
import rs.moma.janus.lokot.Crypto.aesGcmOpen
import rs.moma.janus.lokot.Crypto.aesGcmSeal
import rs.moma.janus.lokot.Crypto.KEY_SIZE
import rs.moma.janus.lokot.Crypto.TAG_SIZE
import rs.moma.janus.lokot.Crypto.hkdf

/**
 * The `.lokot` file format:
 * ```
 * magic      : 6 bytes                   // "LOKOT\0"
 * version    : u8                        // for now always 1
 * headerLen  : u32be                     // length of `header` in bytes
 * header     : headerLen bytes           // plaintext, everything needed before the KEK exists
 * nonce      : 12 bytes                  // nonce for AES-256-GCM of `ciphertext`
 * ciphertext : remaining bytes minus 16  // AES-256-GCM output over the secrets
 * tag        : 16 bytes                  // GCM auth tag for AES-256-GCM of `ciphertext`
 * ```
 *
 * Everything preceding the nonce is the AEAD's associated data, so the version, the salt, the RP id,
 * the wrapped keys, and the non-secret values are all covered by the tag: editing any of them makes
 * the body fail to open rather than silently changing behavior.
 */
class LokotFile private constructor(
    val header: LokotHeader,
    private val associatedData: ByteArray,
    private val nonce: ByteArray,
    private val body: ByteArray,
) {
    fun open(kek: ByteArray): Map<String, String>? = aesGcmOpen(kek, nonce, body, associatedData)?.let(KeyValue::decode)

    companion object {
        private const val PREFIX_SIZE = 11 // magic (6) + version (1) + headerLen (4)
        private val MAGIC = "LOKOT".encodeToByteArray() + byteArrayOf(0)
        const val VERSION = 1

        fun build(header: LokotHeader, secrets: Map<String, String>, kek: ByteArray): ByteArray {
            val headerBytes = KeyValue.encode(header.toMap())
            val associatedData = MAGIC + byteArrayOf(VERSION.toByte()) + headerBytes.size.toBigEndian() + headerBytes

            // for .lokot to be public and tracked by git, every version of the file should have a fresh nonce
            val nonce = randomBytes(NONCE_SIZE)
            val plaintext = KeyValue.encode(secrets)
            val sealed = aesGcmSeal(kek, nonce, plaintext, associatedData)
            plaintext.wipe()

            return associatedData + nonce + sealed
        }

        fun parse(bytes: ByteArray): LokotFile {
            require(bytes.size >= PREFIX_SIZE) { "not a lokot file: too short" }
            require(bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "not a lokot file: bad magic" }

            val version = bytes[MAGIC.size].toInt()
            require(version == VERSION) { "file is format version $version, this lokot understands $VERSION" }

            val headerLength = bytes.readBigEndian(MAGIC.size + 1)
            val bodyStart = PREFIX_SIZE + headerLength
            require(headerLength >= 0 && bodyStart <= bytes.size) { "not a lokot file: header length out of range" }
            require(bytes.size - bodyStart >= NONCE_SIZE + TAG_SIZE) { "not a lokot file: body truncated" }

            return LokotFile(
                header = LokotHeader.fromMap(KeyValue.decode(bytes.copyOfRange(PREFIX_SIZE, bodyStart))),
                associatedData = bytes.copyOfRange(0, bodyStart),
                nonce = bytes.copyOfRange(bodyStart, bodyStart + NONCE_SIZE),
                body = bytes.copyOfRange(bodyStart + NONCE_SIZE, bytes.size),
            )
        }
    }
}

class LokotHeader(
    val salt: ByteArray,
    val rpId: String,
    val credentials: List<WrappedCredential>,
    val plain: Map<String, String>
) {
    init {
        require(salt.size == SALT_SIZE) { "salt must be $SALT_SIZE bytes, was ${salt.size}" }
    }

    fun toMap(): Map<String, String> = buildMap {
        put("salt", salt.toHex())
        put("rpId", rpId)
        credentials.forEachIndexed { index, credential ->
            put("cred.$index.id", credential.id.toHex())
            put("cred.$index.nonce", credential.nonce.toHex())
            put("cred.$index.kek", credential.sealed.toHex())
        }
        plain.forEach { (name, value) -> put("plain.$name", value) }
    }

    companion object {
        const val SALT_SIZE = 32

        fun fromMap(entries: Map<String, String>): LokotHeader {
            val credentials = entries.keys
                .filter { it.startsWith("cred.") && it.endsWith(".id") }
                .map { it.removeSurrounding("cred.", ".id").toInt() }
                .sorted().map { index ->
                    WrappedCredential(
                        id = entries.getValue("cred.$index.id").fromHex(),
                        nonce = entries.getValue("cred.$index.nonce").fromHex(),
                        sealed = entries.getValue("cred.$index.kek").fromHex(),
                    )
                }

            return LokotHeader(
                salt = entries.getValue("salt").fromHex(),
                rpId = entries.getValue("rpId"),
                credentials = credentials,
                plain = entries.filterKeys { it.startsWith("plain.") }.mapKeys { it.key.removePrefix("plain.") },
            )
        }
    }
}

class WrappedCredential(val id: ByteArray, val nonce: ByteArray, val sealed: ByteArray)

object Kek {
    private const val WRAP_INFO = "lokot-kek-wrap-v1"

    private fun derive(hmacOutput: ByteArray) = hkdf(hmacOutput, ByteArray(0), WRAP_INFO.encodeToByteArray(), KEY_SIZE)

    fun wrap(hmacOutput: ByteArray, credentialId: ByteArray, kek: ByteArray): WrappedCredential {
        val wrappingKey = derive(hmacOutput)
        val nonce = randomBytes(NONCE_SIZE)
        val sealed = aesGcmSeal(wrappingKey, nonce, kek, credentialId)
        wrappingKey.wipe()
        return WrappedCredential(credentialId, nonce, sealed)
    }

    fun unwrap(hmacOutput: ByteArray, credential: WrappedCredential): ByteArray? {
        val wrappingKey = derive(hmacOutput)
        val kek = aesGcmOpen(wrappingKey, credential.nonce, credential.sealed, credential.id)
        wrappingKey.wipe()
        return kek
    }
}

private fun Int.toBigEndian(): ByteArray = ByteArray(4) { i -> (this ushr (24 - 8 * i)).toByte() }

private fun ByteArray.readBigEndian(offset: Int): Int =
    (0..3).fold(0) { acc, i -> (acc shl 8) or (this[offset + i].toInt() and 0xFF) }
