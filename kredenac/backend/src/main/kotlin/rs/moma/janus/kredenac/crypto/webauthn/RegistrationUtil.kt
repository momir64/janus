package rs.moma.janus.kredenac.crypto.webauthn

import rs.moma.janus.kredenac.crypto.webauthn.CborValue.Companion.get
import rs.moma.janus.kredenac.crypto.algorithms.VerifyUtil
import rs.moma.janus.kredenac.common.BadRequestException
import rs.moma.janus.kredenac.crypto.algorithms.RsaUtil
import rs.moma.janus.kredenac.common.readBigEndianLong
import rs.moma.janus.kredenac.crypto.algorithms.EcUtil
import rs.moma.janus.kredenac.crypto.algorithms.EdUtil
import rs.moma.janus.kredenac.dtos.Base64Url
import java.security.MessageDigest
import kotlin.uuid.Uuid

class ParsedAttestation(
    val credentialId: ByteArray,
    val publicKey: ByteArray,
    val algorithm: String,
    val aaguid: Uuid?,
    val privezak: Boolean = false
)

suspend fun WebAuthnService.verifyRegistration(
    clientDataJSON: Base64Url,
    attestationObject: Base64Url,
    cookie: String?
): Pair<ParsedAttestation, String> {
    val clientData = clientDataJSON.decode()
    val challenge = decodeClientData(clientData, "webauthn.create").challenge
    val bond = consumeChallengeBond(challenge, cookie)
    val clientDataHash = MessageDigest.getInstance("SHA-256").digest(clientData)
    return parseAttestation(attestationObject, clientDataHash) to bond
}

private fun WebAuthnService.parseAttestation(
    attestationObject: Base64Url, clientDataHash: ByteArray
): ParsedAttestation {
    val attestationObject = attestationObject.decode()
    val attestationMap = CborValue.from(attestationObject) ?: throw BadRequestException("Invalid attestation object")
    val authData = attestationMap["authData"]?.asByteStr() ?: throw BadRequestException("Missing authData")

    verifyRpIdHash(authData)
    verifyUserVerified(authData)

    val parsed = parseAuthData(authData)
    val privezak = isPrivezakAttestation(attestationMap, authData, clientDataHash, parsed.publicKey, attestationRoot)
    return ParsedAttestation(parsed.credentialId, parsed.publicKey, parsed.algorithm, parsed.aaguid, privezak)
}

internal fun parseAuthData(authData: ByteArray): ParsedAttestation {
    if (authData.size < 37) throw BadRequestException("authData too short")

    val flags = authData[32]
    val attestedCredentialDataPresent = (flags.toInt() and 0x40) != 0
    if (!attestedCredentialDataPresent) throw BadRequestException("No attested credential data present")

    val aaguidBytes = authData.copyOfRange(37, 53)
    val aaguid = if (aaguidBytes.all { it.toInt() == 0 }) null else Uuid.fromByteArray(aaguidBytes)

    val credentialIdStart = 55
    if (authData.size < credentialIdStart) throw BadRequestException("authData too short for attested credential data")

    val credentialIdEnd = credentialIdStart + readBigEndianLong(authData, 53, 2).toInt()
    if (authData.size < credentialIdEnd) throw BadRequestException("authData too short for credential id")
    val credentialId = authData.copyOfRange(credentialIdStart, credentialIdEnd)

    val coseKey = CborValue.from(authData, credentialIdEnd)
        ?: throw BadRequestException("Invalid COSE key")

    val kty = coseKey[1]?.asInteger() ?: throw BadRequestException("Missing key type")
    val alg = coseKey[3]?.asInteger() ?: throw BadRequestException("Missing algorithm")
    val algorithm = VerifyUtil(alg)

    val publicKey = when (kty) {
        1L -> {
            val key = coseKey[-2]?.asByteStr() ?: throw BadRequestException("Missing public key")
            EdUtil.toPublicKey(key)
        }
        2L -> {
            val x = coseKey[-2]?.asByteStr() ?: throw BadRequestException("Missing public key x coordinate")
            val y = coseKey[-3]?.asByteStr() ?: throw BadRequestException("Missing public key y coordinate")
            val curve = algorithm.curve ?: throw BadRequestException("Algorithm ${algorithm.name} isn't EC algorithm")
            EcUtil.toPublicKey(curve, x, y)
        }
        3L -> {
            val modulus = coseKey[-1]?.asByteStr() ?: throw BadRequestException("Missing RSA modulus")
            val exponent = coseKey[-2]?.asByteStr() ?: throw BadRequestException("Missing RSA exponent")
            RsaUtil.toPublicKey(modulus, exponent)
        }
        else -> throw BadRequestException("Unsupported key type: $kty")
    }

    return ParsedAttestation(credentialId, publicKey, algorithm.name, aaguid)
}
