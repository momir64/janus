package rs.moma.janus.kredenac.crypto.webauthn

import rs.moma.janus.kredenac.crypto.webauthn.ChallengeKind.REGISTRATION
import rs.moma.janus.kredenac.crypto.webauthn.CborValue.Companion.get
import rs.moma.janus.kredenac.repository.ChallengeRepository
import rs.moma.janus.kredenac.crypto.algorithms.VerifyUtil
import rs.moma.janus.kredenac.crypto.algorithms.EcCurve
import rs.moma.janus.kredenac.crypto.algorithms.RsaUtil
import rs.moma.janus.kredenac.utils.BadRequestException
import rs.moma.janus.kredenac.crypto.algorithms.EcUtil
import rs.moma.janus.kredenac.crypto.algorithms.EdUtil
import rs.moma.janus.kredenac.model.Base64Url

class ParsedAttestation(val credentialId: ByteArray, val publicKey: ByteArray, val algorithm: VerifyUtil, val email: String)

class RegistrationService(
    private val ceremony: WebAuthnCeremony,
    private val challengeRepository: ChallengeRepository
) {
    suspend fun startRegistration(): String {
        val challenge = ceremony.generateChallenge()
        challengeRepository.insert(ceremony.challengeHash(challenge, REGISTRATION))
        return challenge
    }

    suspend fun verifyAndExtract(
        email: String,
        clientDataJSON: Base64Url,
        attestationObject: Base64Url,
        challengeBindingCookie: String?
    ): ParsedAttestation {
        val clientDataBytes = clientDataJSON.decode()
        val clientData = ceremony.decodeClientData(clientDataBytes, "webauthn.create")

        ceremony.verifyChallengeHash(clientData.challenge, REGISTRATION, challengeBindingCookie)

        if (!challengeRepository.consume(ceremony.challengeHash(clientData.challenge, REGISTRATION)))
            throw BadRequestException("No pending registration for this challenge")

        val attestationMap = CborValue.from(attestationObject.decode())
            ?: throw BadRequestException("Invalid attestation object")

        val authData = attestationMap["authData"]?.asByteStr() ?: throw BadRequestException("Missing authData")

        ceremony.verifyRpIdHash(authData)
        ceremony.verifyUserVerified(authData)

        // todo: attStmt (attestation statement) is never verified — fmt/attStmt from
        //  attestationObject are currently ignored entirely, so hardware provenance
        //  is not checked at registration time

        val keys = parseAuthData(authData)
        return ParsedAttestation(keys.credentialId, keys.publicKey, keys.algorithm, email)
    }

    private class RawKeys(val credentialId: ByteArray, val publicKey: ByteArray, val algorithm: VerifyUtil)

    private fun parseAuthData(authData: ByteArray): RawKeys {
        if (authData.size < 37) throw BadRequestException("authData too short")

        val flags = authData[32]
        val attestedCredentialDataPresent = (flags.toInt() and 0x40) != 0
        if (!attestedCredentialDataPresent) throw BadRequestException("No attested credential data present")

        if (authData.size < 55) throw BadRequestException("authData too short for attested credential data")


        val credentialIdStart = 55
        val credentialIdLength = ((authData[53].toInt() and 0xFF) shl 8) or (authData[54].toInt() and 0xFF)
        val credentialIdEnd = credentialIdStart + credentialIdLength
        if (authData.size < credentialIdEnd) throw BadRequestException("authData too short for credential id")
        val credentialId = authData.copyOfRange(credentialIdStart, credentialIdEnd)

        val coseKey = CborValue.from(authData, credentialIdEnd)
            ?: throw BadRequestException("Invalid COSE key")

        val kty = (coseKey[1L] as? CborValue.UInt)?.value
            ?: throw BadRequestException("Missing key type")

        val algValue = when (val alg = coseKey[3L]) {
            is CborValue.NInt -> alg.value
            is CborValue.UInt -> alg.value
            else -> throw BadRequestException("Missing algorithm")
        }
        val algorithm = VerifyUtil(algValue)

        val publicKey = when (kty) {
            1L -> {
                val crv = (coseKey[-1] as? CborValue.UInt)?.value
                if (crv != 6L) throw BadRequestException("Unsupported OKP curve: $crv")
                val key = coseKey[-2]?.asByteStr() ?: throw BadRequestException("Missing public key")
                EdUtil.toPublicKey(key)
            }

            2L -> {
                val crv = coseKey[-1]?.asUInt() ?: throw BadRequestException("Missing curve")
                val x = coseKey[-2]?.asByteStr() ?: throw BadRequestException("Missing public key x coordinate")
                val y = coseKey[-3]?.asByteStr() ?: throw BadRequestException("Missing public key y coordinate")
                val curve = when (crv) {
                    1L -> EcCurve.P256
                    2L -> EcCurve.P384
                    3L -> EcCurve.P521
                    else -> throw BadRequestException("Unsupported curve: $crv")
                }
                EcUtil.toPublicKey(curve, x, y)
            }

            3L -> {
                val modulus = coseKey[-1]?.asByteStr() ?: throw BadRequestException("Missing RSA modulus")
                val exponent = coseKey[-2]?.asByteStr() ?: throw BadRequestException("Missing RSA exponent")
                RsaUtil.toPublicKey(modulus, exponent)
            }

            else -> throw BadRequestException("Unsupported key type: $kty")
        }

        return RawKeys(credentialId, publicKey, algorithm)
    }
}
