package rs.moma.janus.kredenac.service

import rs.moma.janus.kredenac.repository.CredentialRepository
import rs.moma.janus.kredenac.utils.UnauthorizedException
import rs.moma.janus.kredenac.repository.UserRepository
import rs.moma.janus.kredenac.utils.BadRequestException
import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import rs.moma.janus.kredenac.utils.NotFoundException
import java.util.concurrent.ConcurrentHashMap
import rs.moma.janus.kredenac.model.Base64Url
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import java.security.Signature
import kotlin.uuid.Uuid

data class PendingAssertion(val challenge: String, val email: String, val createdAt: Long)

data class AssertionResult(val userId: Uuid, val email: String)

class AssertionService(
    private val userRepository: UserRepository,
    private val credentialRepository: CredentialRepository,
    private val rpId: String,
    private val rpOrigin: String
) {
    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }
    private val pending = ConcurrentHashMap<String, PendingAssertion>()

    suspend fun startAssertion(email: String): PendingAssertion {
        val user = userRepository.findByEmail(email) ?: throw NotFoundException("User not found")
        val credentials = credentialRepository.findAllForUser(user.id)
        if (credentials.isEmpty()) throw NotFoundException("No credentials registered for user")

        val challengeBytes = ByteArray(32)
        secureRandom.nextBytes(challengeBytes)
        val challenge = Base64.UrlSafe.withPadding(ABSENT).encode(challengeBytes)

        val assertion = PendingAssertion(challenge, email, System.currentTimeMillis())
        pending[email] = assertion
        return assertion
    }

    suspend fun verify(
        email: String, credentialId: Base64Url, clientDataJSON: Base64Url,
        authenticatorData: Base64Url, signature: Base64Url
    ): AssertionResult {
        val pendingAssertion = pending.remove(email) ?: throw BadRequestException("No pending assertion for user")

        if (System.currentTimeMillis() - pendingAssertion.createdAt > 5 * 60 * 1000)
            throw BadRequestException("Assertion challenge expired")

        val clientDataBytes = clientDataJSON.decode()
        val clientData = json.decodeFromString<ClientDataJson>(String(clientDataBytes, Charsets.UTF_8))

        if (clientData.type != "webauthn.get") throw BadRequestException("Unexpected clientData type")
        if (clientData.challenge != pendingAssertion.challenge) throw BadRequestException("Challenge mismatch")
        if (clientData.origin != rpOrigin) throw BadRequestException("Origin mismatch")

        val credentialId = credentialId.decode()
        val credential = credentialRepository.findByCredentialId(credentialId)
            ?: throw UnauthorizedException("Unknown credential")

        val authenticatorData = authenticatorData.decode()
        if (authenticatorData.size < 37) throw BadRequestException("authenticatorData too short")

        val rpIdHash = authenticatorData.copyOfRange(0, 32)
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray())
        if (!MessageDigest.isEqual(rpIdHash, expectedHash)) throw UnauthorizedException("RP ID hash mismatch")

        val signCount = ((authenticatorData[33].toInt() and 0xFF).toLong() shl 24) or
                ((authenticatorData[34].toInt() and 0xFF).toLong() shl 16) or
                ((authenticatorData[35].toInt() and 0xFF).toLong() shl 8) or
                (authenticatorData[36].toInt() and 0xFF).toLong()

        if (signCount != 0L && signCount <= credential.signCount)
            throw UnauthorizedException("Sign count did not increase, possible cloned authenticator")

        val clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataBytes)
        val signedData = authenticatorData + clientDataHash

        val publicKey = EcKeyUtil.reconstructPublicKey(credential.publicKeyX, credential.publicKeyY)
        val signatureCheck = Signature.getInstance("SHA256withECDSA")
        signatureCheck.initVerify(publicKey)
        signatureCheck.update(signedData)

        if (!signatureCheck.verify(signature.decode()))
            throw UnauthorizedException("Signature verification failed")

        credentialRepository.updateSignCount(credential.id, signCount)

        val user = userRepository.findById(credential.userId) ?: throw NotFoundException("User not found")
        return AssertionResult(user.id, user.email)
    }
}
