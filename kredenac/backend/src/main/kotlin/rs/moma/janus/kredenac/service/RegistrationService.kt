package rs.moma.janus.kredenac.service

import rs.moma.janus.kredenac.utils.BadRequestException
import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import rs.moma.janus.kredenac.utils.CborDecoder
import java.util.concurrent.ConcurrentHashMap
import rs.moma.janus.kredenac.model.Base64Url
import rs.moma.janus.kredenac.utils.CborValue
import rs.moma.janus.kredenac.utils.byIntKey
import rs.moma.janus.kredenac.utils.textStr
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64

@Serializable
data class ClientDataJson(val type: String, val challenge: String, val origin: String)

data class PendingRegistration(val challenge: String, val email: String, val createdAt: Long)

class ParsedAttestation(val credentialId: ByteArray, val publicKeyX: ByteArray, val publicKeyY: ByteArray, val email: String)

class RegistrationService(private val rpId: String, private val rpOrigin: String) {
    private val secureRandom = SecureRandom()
    private val json = Json { ignoreUnknownKeys = true }
    private val pending = ConcurrentHashMap<String, PendingRegistration>()

    fun startRegistration(email: String): PendingRegistration {
        val challengeBytes = ByteArray(32)
        secureRandom.nextBytes(challengeBytes)
        val challenge = Base64.UrlSafe.withPadding(ABSENT).encode(challengeBytes)

        val registration = PendingRegistration(challenge, email, System.currentTimeMillis())
        pending[email] = registration
        return registration
    }

    fun verifyAndExtract(email: String, clientDataJSON: Base64Url, attestationObject: Base64Url): ParsedAttestation {
        val registration = pending.remove(email) ?: throw BadRequestException("No pending registration for user")

        if (System.currentTimeMillis() - registration.createdAt > 5 * 60 * 1000)
            throw BadRequestException("Registration challenge expired")

        val clientDataBytes = clientDataJSON.decode()
        val clientData = json.decodeFromString<ClientDataJson>(String(clientDataBytes, Charsets.UTF_8))

        if (clientData.type != "webauthn.create") throw BadRequestException("Unexpected clientData type")
        if (clientData.challenge != registration.challenge) throw BadRequestException("Challenge mismatch")
        if (clientData.origin != rpOrigin) throw BadRequestException("Origin mismatch")

        val attestationObjectBytes = attestationObject.decode()
        val attestationMap = CborDecoder(attestationObjectBytes).decode() as? CborValue.Map
            ?: throw BadRequestException("Invalid attestation object")

        val authData = (attestationMap.textStr("authData") as? CborValue.ByteStr)?.value
            ?: throw BadRequestException("Missing authData")

        val keys = parseAuthData(authData)
        return ParsedAttestation(keys.credentialId, keys.publicKeyX, keys.publicKeyY, registration.email)
    }

    private class RawKeys(val credentialId: ByteArray, val publicKeyX: ByteArray, val publicKeyY: ByteArray)

    private fun parseAuthData(authData: ByteArray): RawKeys {
        if (authData.size < 37) throw BadRequestException("authData too short")

        val rpIdHash = authData.copyOfRange(0, 32)
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(rpId.toByteArray())
        if (!MessageDigest.isEqual(rpIdHash, expectedHash)) throw BadRequestException("RP ID hash mismatch")

        val flags = authData[32]
        val attestedCredentialDataPresent = (flags.toInt() and 0x40) != 0
        if (!attestedCredentialDataPresent) throw BadRequestException("No attested credential data present")

        if (authData.size < 55) throw BadRequestException("authData too short for attested credential data")

        val credentialIdLength = ((authData[53].toInt() and 0xFF) shl 8) or (authData[54].toInt() and 0xFF)
        val credentialIdStart = 55
        if (authData.size < credentialIdStart + credentialIdLength) throw BadRequestException("authData too short for credential id")
        val credentialId = authData.copyOfRange(credentialIdStart, credentialIdStart + credentialIdLength)

        val coseKeyDecoder = CborDecoder(authData, credentialIdStart + credentialIdLength)
        val coseKey = coseKeyDecoder.decode() as? CborValue.Map
            ?: throw BadRequestException("Invalid COSE key")

        val kty = (coseKey.byIntKey(1) as? CborValue.UInt)?.value
        if (kty != 2L) throw BadRequestException("Only EC2 keys are supported")

        val alg = (coseKey.byIntKey(3) as? CborValue.NInt)?.value
        if (alg != -7L) throw BadRequestException("Only ES256 is supported")

        val x = (coseKey.byIntKey(-2) as? CborValue.ByteStr)?.value
            ?: throw BadRequestException("Missing public key x coordinate")
        val y = (coseKey.byIntKey(-3) as? CborValue.ByteStr)?.value
            ?: throw BadRequestException("Missing public key y coordinate")

        return RawKeys(credentialId, x, y)
    }
}
