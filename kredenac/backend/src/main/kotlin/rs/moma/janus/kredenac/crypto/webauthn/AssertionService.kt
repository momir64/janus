package rs.moma.janus.kredenac.crypto.webauthn

import rs.moma.janus.kredenac.crypto.webauthn.ChallengeKind.ASSERTION
import rs.moma.janus.kredenac.repository.CredentialRepository
import rs.moma.janus.kredenac.repository.ChallengeRepository
import rs.moma.janus.kredenac.crypto.algorithms.VerifyUtil
import rs.moma.janus.kredenac.utils.UnauthorizedException
import rs.moma.janus.kredenac.repository.UserRepository
import rs.moma.janus.kredenac.utils.BadRequestException
import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import rs.moma.janus.kredenac.utils.NotFoundException
import rs.moma.janus.kredenac.model.Base64Url
import org.slf4j.LoggerFactory.getLogger
import java.security.MessageDigest
import kotlin.io.encoding.Base64
import kotlin.uuid.Uuid

data class PendingAssertionStart(val challenge: String, val allowedCredentialIds: List<String>?)

data class AssertionResult(val userId: Uuid, val email: String)

class AssertionService(
    private val ceremony: WebAuthnCeremony,
    private val challengeRepository: ChallengeRepository,
    private val userRepository: UserRepository,
    private val credentialRepository: CredentialRepository
) {
    private val log = getLogger(AssertionService::class.java)

    suspend fun startAssertion(email: String?): PendingAssertionStart {
        val challenge = ceremony.generateChallenge()
        challengeRepository.insert(ceremony.challengeHash(challenge, ASSERTION))

        val allowedCredentialIds = email?.let {
            val user = userRepository.findByEmail(it) ?: throw NotFoundException("User not found")
            credentialRepository.findAllForUser(user.id).map { c ->
                Base64.UrlSafe.withPadding(ABSENT).encode(c.credentialId)
            }
        }

        return PendingAssertionStart(challenge, allowedCredentialIds)
    }

    suspend fun verify(
        credentialId: Base64Url,
        clientDataJSON: Base64Url,
        authenticatorData: Base64Url,
        signature: Base64Url,
        challengeBindingCookie: String?
    ): AssertionResult {
        val clientDataBytes = clientDataJSON.decode()
        val clientData = ceremony.decodeClientData(clientDataBytes, "webauthn.get")

        ceremony.verifyChallengeHash(clientData.challenge, ASSERTION, challengeBindingCookie)

        if (!challengeRepository.consume(ceremony.challengeHash(clientData.challenge, ASSERTION)))
            throw BadRequestException("No pending assertion for this challenge")

        val credentialIdBytes = credentialId.decode()
        val credential = credentialRepository.findByCredentialId(credentialIdBytes)
            ?: throw UnauthorizedException("Unknown credential")

        val authenticatorDataBytes = authenticatorData.decode()
        ceremony.verifyRpIdHash(authenticatorDataBytes)
        ceremony.verifyUserVerified(authenticatorDataBytes)

        if (authenticatorDataBytes.size < 37) throw BadRequestException("authenticatorData too short")
        val signCount = readBigEndianLong(authenticatorDataBytes, 33, 4)

        if (signCount != 0L && signCount <= credential.signCount) {
            log.warn("Possible cloned authenticator: credentialId=${credential.id} userId=${credential.userId}")
            // todo: on detection, also send an email notification to the user and invalidate/revoke this credential
            throw UnauthorizedException("Sign count did not increase, possible cloned authenticator")
        }

        val clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataBytes)
        val signedData = authenticatorDataBytes + clientDataHash

        val algorithm = VerifyUtil(credential.algorithm)
        if (!algorithm.verify(credential.publicKey, signedData, signature.decode()))
            throw UnauthorizedException("Signature verification failed")

        credentialRepository.updateSignCount(credential, signCount)

        val user = userRepository.findById(credential.userId) ?: throw NotFoundException("User not found")
        return AssertionResult(user.id, user.email)
    }
}
