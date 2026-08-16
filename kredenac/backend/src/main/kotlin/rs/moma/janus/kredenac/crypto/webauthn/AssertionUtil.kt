package rs.moma.janus.kredenac.crypto.webauthn

import rs.moma.janus.kredenac.crypto.algorithms.VerifyUtil
import rs.moma.janus.kredenac.utils.UnauthorizedException
import rs.moma.janus.kredenac.utils.BadRequestException
import rs.moma.janus.kredenac.model.Base64Url
import org.slf4j.LoggerFactory.getLogger
import java.security.MessageDigest
import kotlin.uuid.Uuid

private object AssertionLog
private val log = getLogger(AssertionLog::class.java)

suspend fun WebAuthnService.verifyLogin(
    credentialId: Base64Url,
    clientDataJSON: Base64Url,
    authenticatorData: Base64Url,
    signature: Base64Url,
    cookie: String?
): Uuid {
    val clientDataBytes = clientDataJSON.decode()
    val clientData = decodeClientData(clientDataBytes, "webauthn.get")

    verifyChallengeSession(clientData.challenge, cookie)

    val credentialIdBytes = credentialId.decode()
    val credential = credentialRepository.findByCredentialId(credentialIdBytes) ?: throw UnauthorizedException("Unknown credential")

    val authenticatorDataBytes = authenticatorData.decode()
    verifyRpIdHash(authenticatorDataBytes)
    verifyUserVerified(authenticatorDataBytes)

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
    return credential.userId
}
