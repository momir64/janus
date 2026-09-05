package rs.moma.janus.privezak.provider

import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.security.interfaces.ECPublicKey
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import java.security.PublicKey
import java.math.BigInteger

// Authenticator flags
private const val USER_VERIFIED = 0x04
private const val USER_PRESENT = 0x01
private const val ATTESTED = 0x40

private val AAGUID = ByteArray(16) // todo: define aaguid for privezak and add it to kredenac

internal fun ByteArray.sha256(): ByteArray = MessageDigest.getInstance("SHA-256").digest(this)

internal fun jsonString(json: String, name: String): String? = runCatching {
    Json.parseToJsonElement(json).jsonObject[name]?.jsonPrimitive?.content
}.getOrNull()

private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)
internal fun String.decodeBase64url(): ByteArray = base64Url.decode(this)
internal fun ByteArray.base64url(): String = base64Url.encode(this)

internal object Cbor {
    fun map(entries: Int) = header(0xA0, entries)
    fun array(entries: Int) = header(0x80, entries)
    fun bytes(value: ByteArray) = header(0x40, value.size) + value
    fun text(value: String) = header(0x60, value.length) + value.toByteArray()
    fun int(value: Int) = if (value >= 0) header(0x00, value) else header(0x20, -1 - value)

    private fun header(major: Int, value: Int): ByteArray = when {
        value < 24 -> byteArrayOf((major or value).toByte())
        value < 256 -> byteArrayOf((major or 24).toByte(), value.toByte())
        else -> byteArrayOf((major or 25).toByte(), (value shr 8).toByte(), value.toByte())
    }
}

internal fun coseKey(publicKey: PublicKey): ByteArray {
    val point = (publicKey as ECPublicKey).w
    return Cbor.map(5) +
            Cbor.int(1) + Cbor.int(2) +   // kty: EC2
            Cbor.int(3) + Cbor.int(-7) +  // alg: ES256
            Cbor.int(-1) + Cbor.int(1) +  // crv: P-256
            Cbor.int(-2) + Cbor.bytes(point.affineX.toFixed32()) +
            Cbor.int(-3) + Cbor.bytes(point.affineY.toFixed32())
}

private fun BigInteger.toFixed32(): ByteArray {
    val bytes = toByteArray().dropWhile { it == 0.toByte() }.toByteArray()
    return ByteArray(32 - bytes.size) + bytes
}

internal fun Int.toBigEndian(): ByteArray =
    byteArrayOf((this shr 24).toByte(), (this shr 16).toByte(), (this shr 8).toByte(), toByte())

internal fun authenticatorData(
    rpId: String,
    signCount: Int,
    credentialId: ByteArray? = null,
    publicKey: PublicKey? = null
): ByteArray {
    val attested = credentialId != null && publicKey != null
    val flags = USER_PRESENT or USER_VERIFIED or if (attested) ATTESTED else 0
    val header = rpId.toByteArray().sha256() + byteArrayOf(flags.toByte()) + signCount.toBigEndian()
    if (!attested) return header
    val credentialIdLength = credentialId.size.toBigEndian().takeLast(2).toByteArray()
    return header + AAGUID + credentialIdLength + credentialId + coseKey(publicKey)
}

internal fun noneAttestation(authenticatorData: ByteArray): ByteArray =
    attestationObject("none", Cbor.map(0), authenticatorData)

internal fun androidKeyAttestation(
    authenticatorData: ByteArray,
    signature: ByteArray,
    chain: List<ByteArray>
): ByteArray {
    val certificates = chain.fold(ByteArray(0)) { all, next -> all + Cbor.bytes(next) }
    val attStmt = Cbor.map(3) +
            Cbor.text("alg") + Cbor.int(-7) +
            Cbor.text("sig") + Cbor.bytes(signature) +
            Cbor.text("x5c") + Cbor.array(chain.size) + certificates
    return attestationObject("android-key", attStmt, authenticatorData)
}

private fun attestationObject(fmt: String, attStmt: ByteArray, authenticatorData: ByteArray) =
    Cbor.map(3) +
            Cbor.text("fmt") + Cbor.text(fmt) +
            Cbor.text("attStmt") + attStmt +
            Cbor.text("authData") + Cbor.bytes(authenticatorData)

// When the request originated from arbitrary app instead of the browser
internal fun apkKeyHashOrigin(signingCertificate: ByteArray): String =
    "android:apk-key-hash:" + signingCertificate.sha256().base64url()
