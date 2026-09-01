package rs.moma.janus.kredenac.crypto.webauthn

import rs.moma.janus.kredenac.common.UnauthorizedException
import rs.moma.janus.kredenac.crypto.algorithms.VerifyUtil
import rs.moma.janus.kredenac.common.BadRequestException
import rs.moma.janus.kredenac.common.readBigEndianLong
import rs.moma.janus.kredenac.dtos.Base64Url
import org.slf4j.LoggerFactory.getLogger
import java.security.MessageDigest
import kotlin.uuid.Uuid

private object AssertionLog

private val log = getLogger(AssertionLog::class.java)

sealed class LoginOutcome {
    data class Success(val userId: Uuid, val credentialId: Uuid) : LoginOutcome()
    data class CloneDetected(val userId: Uuid, val credentialId: Uuid) : LoginOutcome()
}

suspend fun WebAuthnService.verifyLogin(
    credentialId: Base64Url, clientDataJSON: Base64Url, authenticatorData: Base64Url,
    signature: Base64Url, cookie: String?, ip: String?, location: String?
): LoginOutcome {
    val clientDataBytes = clientDataJSON.decode()
    val clientData = decodeClientData(clientDataBytes, "webauthn.get")

    verifyChallengeSession(clientData.challenge, cookie)

    val credential = credentialRepository.findByCredentialId(credentialId.decode())
        ?: throw UnauthorizedException("Unknown credential", "passkey_unknown")

    val authenticatorDataBytes = authenticatorData.decode()
    verifyRpIdHash(authenticatorDataBytes)
    verifyUserVerified(authenticatorDataBytes)

    if (authenticatorDataBytes.size < 37) throw BadRequestException("authenticatorData too short")
    val signCount = readBigEndianLong(authenticatorDataBytes, 33, 4)

    if (signCount != 0L && signCount <= credential.signCount) {
        log.warn("Possible cloned authenticator: credentialId=${credential.id} userId=${credential.userId}")
        return LoginOutcome.CloneDetected(credential.userId, credential.id)
    }

    val clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientDataBytes)
    val signedData = authenticatorDataBytes + clientDataHash

    val algorithm = VerifyUtil(credential.algorithm)
    if (!algorithm.verify(credential.publicKey, signedData, signature.decode()))
        throw UnauthorizedException("Signature verification failed")

    credentialRepository.recordUse(credential, signCount, ip, location)
    return LoginOutcome.Success(credential.userId, credential.id)
}
