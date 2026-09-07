package rs.moma.janus.lokot.checks

import rs.moma.janus.lokot.externals.fromHex
import rs.moma.janus.lokot.externals.Crypto

internal fun cryptoChecks(): List<Check> {
    val cryptoBug = CheckGroup(CRYPTO_BUG)
    val sha = cryptoBug.section("SHA-256")
    val hmac = cryptoBug.section("HMAC-SHA-256 (RFC 4231)")
    val hkdf = cryptoBug.section("HKDF-SHA-256 (RFC 5869)")
    val aes = cryptoBug.section("AES-256-GCM")
    val nonceBug = CheckGroup(NONCE_BUG).section("random")

    val message = "the quick brown fox"
    val key = "0b".repeat(32).fromHex()
    val nonce = "00".repeat(12).fromHex()
    val aad = "lokot header".encodeToByteArray()

    fun sealed() = Crypto.aesGcmSeal(key, nonce, message.encodeToByteArray(), aad)
    fun byteRange(range: IntRange) = ByteArray(range.count()) { range.first.plus(it).toByte() }

    return listOf(
        sha.equal("empty", "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855") {
            Crypto.sha256(ByteArray(0))
        },
        sha.equal("abc", "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad") {
            Crypto.sha256("abc".encodeToByteArray())
        },

        hmac.equal("test", "b0344c61d8db38535ca8afceaf0bf12b881dc200c9833da726e9376c2e32cff7"
        ) {
            Crypto.hmacSha256("0b".repeat(20).fromHex(), "Hi There".encodeToByteArray())
        },

        hkdf.equal("case 1",
            "3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"
        ) {
            Crypto.hkdf(
                ikm = "0b".repeat(22).fromHex(),
                salt = "000102030405060708090a0b0c".fromHex(),
                info = "f0f1f2f3f4f5f6f7f8f9".fromHex(),
                length = 42,
            )
        },
        hkdf.equal("case 2 (long inputs, multi-block expand)",
            "b11e398dc80327a1c8e7f78c596a49344f012eda2d4efad8a050cc4c19afa97c59045a99cac7827271cb41" +
                    "c65e590e09da3275600c2f09b8367793a9aca3db71cc30c58179ec3e87c14c01d5c1f3434f1d87"
        ) {
            Crypto.hkdf(
                ikm = byteRange(0x00..0x4f),
                salt = byteRange(0x60..0xaf),
                info = byteRange(0xb0..0xff),
                length = 82,
            )
        },
        hkdf.equal("case 3 (empty salt and info)",
            "8da4e775a563c18f715f802a063c5a31b8a11f5c5ee1879ec3454e5f3c738d2d9d201395faa4b61a96c8"
        ) {
            Crypto.hkdf(ikm = "0b".repeat(22).fromHex(), salt = ByteArray(0), info = ByteArray(0), length = 42)
        },

        aes.equal("matches across crypto implementations",
            "5cb224d3f0eee5c2b990be6f52afdc3cdb37d3ec89c985779415b43761f3aa41e16cf1"
        ) { sealed() },

        aes.equals("tag appended", message.length + Crypto.TAG_SIZE) { sealed().size },
        aes.holds("round trip") {
            Crypto.aesGcmOpen(key, nonce, sealed(), aad)?.decodeToString() == message
        },
        aes.holds("empty plaintext round trips") {
            Crypto.aesGcmOpen(key, nonce, Crypto.aesGcmSeal(key, nonce, ByteArray(0), aad), aad)?.isEmpty() == true
        },
        aes.holds("rejects tampered ciphertext") {
            val broken = sealed().also { it[0] = (it[0].toInt() xor 1).toByte() }
            Crypto.aesGcmOpen(key, nonce, broken, aad) == null
        },
        aes.holds("rejects tampered tag") {
            val broken = sealed().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
            Crypto.aesGcmOpen(key, nonce, broken, aad) == null
        },
        aes.holds("rejects modified aad") {
            Crypto.aesGcmOpen(key, nonce, sealed(), "lokot Selftest".encodeToByteArray()) == null
        },
        aes.holds("rejects wrong key") {
            Crypto.aesGcmOpen("01".repeat(32).fromHex(), nonce, sealed(), aad) == null
        },
        aes.holds("rejects wrong nonce") {
            Crypto.aesGcmOpen(key, "01".repeat(12).fromHex(), sealed(), aad) == null
        },

        nonceBug.equals("returns the requested length", 48) { Crypto.randomBytes(48).size },
        nonceBug.holds("does not repeat") {
            !Crypto.randomBytes(32).contentEquals(Crypto.randomBytes(32))
        }
    )
}

