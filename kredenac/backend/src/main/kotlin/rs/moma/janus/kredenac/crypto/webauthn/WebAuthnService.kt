package rs.moma.janus.kredenac.crypto.webauthn

import rs.moma.janus.kredenac.repositories.CredentialRepository
import rs.moma.janus.kredenac.repositories.TokenRepository
import rs.moma.janus.kredenac.common.BadRequestException
import rs.moma.janus.kredenac.crypto.algorithms.HmacUtil
import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import kotlin.time.Duration.Companion.minutes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64

@Serializable
data class ClientDataJson(val type: String, val challenge: String, val origin: String)

data class ChallengeSession(val challenge: String, val cookie: String)

class WebAuthnService(
    private val rpId: String,
    private val rpOrigin: String,
    private val hmacSecret: ByteArray,
    private val tokenRepository: TokenRepository,
    internal val credentialRepository: CredentialRepository
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val secureRandom = SecureRandom()
    private val challengeTtl = 3.minutes

    suspend fun start(token: String? = null): ChallengeSession {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val challenge = Base64.UrlSafe.withPadding(ABSENT).encode(bytes)
        val sessionCookie = HmacUtil.hash(hmacSecret, "$challenge:cookie")
        tokenRepository.insert(challenge, challengeTtl, token, true)
        return ChallengeSession(challenge, sessionCookie)
    }

    suspend fun verifyChallengeSession(challenge: String, cookie: String?) {
        verifyChallengeCookie(challenge, cookie)
        if (!tokenRepository.consumePresence(challenge))
            throw BadRequestException("Challenge isn't pending, it might have expired")
    }

    suspend fun consumeChallengeBond(challenge: String, cookie: String?): String {
        verifyChallengeCookie(challenge, cookie)
        return tokenRepository.consumeWithBond(challenge)
    }

    private fun verifyChallengeCookie(challenge: String, cookie: String?) {
        val sessionCookie = HmacUtil.hash(hmacSecret, "$challenge:cookie")
        if (cookie == null || sessionCookie != cookie)
            throw BadRequestException("Challenge session cookie is missing or isn't valid")
    }

    fun decodeClientData(bytes: ByteArray, expectedType: String): ClientDataJson {
        val clientData = json.decodeFromString<ClientDataJson>(String(bytes))
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

    fun verifyUserVerified(authData: ByteArray) {
        if (authData.size < 33) throw BadRequestException("authData too short")
        if ((authData[32].toInt() and 0x01) == 0) throw BadRequestException("User presence flag not set")
        if ((authData[32].toInt() and 0x04) == 0) throw BadRequestException("User verification not performed")
    }
}
