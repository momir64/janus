package rs.moma.janus.lokot.files

import rs.moma.janus.lokot.externals.Crypto.randomBytes
import rs.moma.janus.lokot.externals.Crypto.NONCE_SIZE
import rs.moma.janus.lokot.externals.Crypto.aesGcmOpen
import rs.moma.janus.lokot.externals.Crypto.aesGcmSeal
import rs.moma.janus.lokot.externals.Crypto.KEY_SIZE
import rs.moma.janus.lokot.externals.Crypto.TAG_SIZE
import rs.moma.janus.lokot.externals.Crypto.hkdf
import rs.moma.janus.lokot.externals.fromHex
import rs.moma.janus.lokot.externals.toHex
import rs.moma.janus.lokot.externals.wipe

/**
 * The `.lokot` file format:
 * ```
 * magic      : 6 bytes                   // "LOKOT\0"
 * version    : u8                        // version of the format
 * headerLen  : u32be                     // length of `header` in bytes, the newlines excluded
 * newline    : 1 byte                    // so the header starts on a line of its own
 * header     : headerLen bytes           // plaintext, everything needed before the KEK exists
 * newline    : 1 byte                    // so the body does not run onto the header's last line
 * nonce      : 12 bytes                  // nonce for AES-256-GCM of `ciphertext`
 * ciphertext : remaining bytes minus 16  // AES-256-GCM output over the schema and the secrets
 * tag        : 16 bytes                  // GCM auth tag for AES-256-GCM of `ciphertext`
 * ```
 *
 * Everything preceding the nonce is the AEAD's associated data, so the version, the salt, the
 * wrapped keys, and the RP id each was enrolled under are all covered by the tag: editing any of
 * them makes the body fail to open rather than silently changing behavior.
 */
internal class LokotFile private constructor(
    val header: LokotHeader,
    private val associatedData: ByteArray,
    private val nonce: ByteArray,
    private val body: ByteArray,
) {
    fun open(kek: ByteArray): VaultBody? = aesGcmOpen(kek, nonce, body, associatedData)?.let(VaultBody::decode)

    companion object {
        private const val PREFIX_SIZE = 11 // magic (6) + version (1) + headerLen (4)
        private const val NEWLINE = '\n'.code.toByte()
        private val MAGIC = "LOKOT".encodeToByteArray() + byteArrayOf(0)
        const val VERSION = 1

        fun build(header: LokotHeader, body: VaultBody, kek: ByteArray): ByteArray {
            val headerBytes = PlaintextFile.encode(header.toMap())
            val associatedData = MAGIC + byteArrayOf(VERSION.toByte()) + headerBytes.size.toBigEndian() +
                    byteArrayOf(NEWLINE) + headerBytes + byteArrayOf(NEWLINE)

            // for .lokot to be public and tracked by git, every version of the file should have a fresh nonce
            val nonce = randomBytes(NONCE_SIZE)
            val plaintext = body.encode()
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
            require(headerLength in 0..(bytes.size - PREFIX_SIZE - 2)) {
                "not a lokot file: header length out of range"
            }

            val headerStart = PREFIX_SIZE + 1
            val bodyStart = headerStart + headerLength + 1
            require(bytes[PREFIX_SIZE] == NEWLINE && bytes[bodyStart - 1] == NEWLINE) {
                "not a lokot file: the header is not framed by newlines"
            }
            require(bytes.size - bodyStart >= NONCE_SIZE + TAG_SIZE) { "not a lokot file: body truncated" }

            return LokotFile(
                header = LokotHeader.fromMap(PlaintextFile.decodeText(bytes.copyOfRange(headerStart, bodyStart - 1))),
                associatedData = bytes.copyOfRange(0, bodyStart),
                nonce = bytes.copyOfRange(bodyStart, bodyStart + NONCE_SIZE),
                body = bytes.copyOfRange(bodyStart + NONCE_SIZE, bytes.size),
            )
        }
    }
}

internal class LokotHeader(val project: String, val salt: ByteArray, val credentials: List<WrappedCredential>) {
    init {
        require(salt.size == SALT_SIZE) { "salt must be $SALT_SIZE bytes, was ${salt.size}" }
    }

    fun credentialsFor(rpId: String): List<WrappedCredential> = credentials.filter { it.rpId == rpId }

    fun toMap(): Map<String, String> = buildMap {
        put("project", project)
        put("salt", salt.toHex())
        credentials.forEachIndexed { index, credential ->
            put("cred.$index.id", credential.id.toHex())
            put("cred.$index.rp", credential.rpId)
            put("cred.$index.nonce", credential.nonce.toHex())
            put("cred.$index.kek", credential.sealed.toHex())
        }
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
                        rpId = entries.getValue("cred.$index.rp"),
                        nonce = entries.getValue("cred.$index.nonce").fromHex(),
                        sealed = entries.getValue("cred.$index.kek").fromHex(),
                    )
                }

            return LokotHeader(
                project = entries.getValue("project"),
                salt = entries.getValue("salt").fromHex(),
                credentials = credentials,
            )
        }
    }
}

internal class WrappedCredential(val id: ByteArray, val rpId: String, val nonce: ByteArray, val sealed: ByteArray)

internal object Kek {
    private const val WRAP_INFO = "lokot-kek-wrap-v1"

    private fun derive(hmacOutput: ByteArray) = hkdf(hmacOutput, ByteArray(0), WRAP_INFO.encodeToByteArray(), KEY_SIZE)

    fun wrap(hmacOutput: ByteArray, credentialId: ByteArray, rpId: String, kek: ByteArray): WrappedCredential {
        val wrappingKey = derive(hmacOutput)
        val nonce = randomBytes(NONCE_SIZE)
        val sealed = aesGcmSeal(wrappingKey, nonce, kek, credentialId)
        wrappingKey.wipe()
        return WrappedCredential(credentialId, rpId, nonce, sealed)
    }

    fun unwrap(hmacOutput: ByteArray, credential: WrappedCredential): ByteArray? {
        val wrappingKey = derive(hmacOutput)
        val kek = aesGcmOpen(wrappingKey, credential.nonce, credential.sealed, credential.id)
        wrappingKey.wipe()
        return kek
    }
}

internal fun Int.toBigEndian(): ByteArray = ByteArray(4) { i -> (this ushr (24 - 8 * i)).toByte() }

internal fun ByteArray.readBigEndian(offset: Int): Int =
    (0..3).fold(0) { acc, i -> (acc shl 8) or (this[offset + i].toInt() and 0xFF) }
