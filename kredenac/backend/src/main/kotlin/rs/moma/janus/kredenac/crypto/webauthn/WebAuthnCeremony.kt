package rs.moma.janus.kredenac.crypto.webauthn

import rs.moma.janus.kredenac.crypto.algorithms.HmacUtil
import rs.moma.janus.kredenac.utils.BadRequestException
import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64

@Serializable
data class ClientDataJson(val type: String, val challenge: String, val origin: String)

class WebAuthnCeremony(
    private val rpId: String,
    private val rpOrigin: String,
    private val hmacSecret: ByteArray
) {
    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }

    fun generateChallenge(): String {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        return Base64.UrlSafe.withPadding(ABSENT).encode(bytes)
    }

    fun challengeHash(challenge: String, kind: ChallengeKind): String =
        HmacUtil.hash(hmacSecret, "${kind.value}:$challenge")

    fun verifyChallengeHash(challenge: String, kind: ChallengeKind, cookieValue: String?) {
        if (cookieValue == null || cookieValue != challengeHash(challenge, kind))
            throw BadRequestException("Challenge binding mismatch")
    }

    fun decodeClientData(bytes: ByteArray, expectedType: String): ClientDataJson {
        val clientData = json.decodeFromString<ClientDataJson>(String(bytes, Charsets.UTF_8))
        if (clientData.type != expectedType) throw BadRequestException("Unexpected clientData type")
        if (clientData.origin != rpOrigin) throw BadRequestException("Origin mismatch")
        return clientData
    }

    fun verifyRpIdHash(authData: ByteArray) {
        if (authData.size < 32) throw BadRequestException("authData too short")
        val rpIdHash = authData.copyOfRange(0, 32)
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray())
        if (!MessageDigest.isEqual(rpIdHash, expectedHash)) throw BadRequestException("RP ID hash mismatch")
    }

    fun verifyUserPresent(authData: ByteArray) {
        if (authData.size < 33) throw BadRequestException("authData too short")
        if ((authData[32].toInt() and 0x01) == 0) throw BadRequestException("User presence flag not set")
    }

    fun verifyUserVerified(authData: ByteArray) {
        verifyUserPresent(authData)
        if ((authData[32].toInt() and 0x04) == 0) throw BadRequestException("User verification required but not performed")
    }
}
