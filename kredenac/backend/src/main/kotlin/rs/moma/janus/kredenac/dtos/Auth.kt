package rs.moma.janus.kredenac.dtos

import kotlin.io.encoding.Base64.PaddingOption.PRESENT_OPTIONAL
import rs.moma.janus.kredenac.common.BadRequestException
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

@JvmInline
@Serializable
value class Base64Url(val value: String) {
    fun decode(): ByteArray = try {
        Base64.UrlSafe.withPadding(PRESENT_OPTIONAL).decode(value)
    } catch (_: IllegalArgumentException) {
        throw BadRequestException("Malformed base64url value")
    }
}

@Serializable
data class SessionResponse(val csrfToken: String, val expiresIn: Long)

@Serializable
data class RegisterVerifyRequest(val email: String)

@Serializable
data class TokenDto(val token: String)

@Serializable
data class ChallengeResponse(
    val excludeCredentials: List<String>,
    val challenge: String,
    val rpId: String,
    val email: String? = null
)

@Serializable
data class AttestationRequest(
    val clientDataJSON: Base64Url,
    val attestationObject: Base64Url
)

@Serializable
data class AssertionRequest(
    val credentialId: Base64Url,
    val clientDataJSON: Base64Url,
    val authenticatorData: Base64Url,
    val signature: Base64Url
)

@Serializable
data class CredentialDto(
    val id: String,
    val deviceName: String?,
    val currentSession: Boolean,
    val createdAt: String,
    val lastUsedAt: String?,
    val lastUsedIp: String?,
    val lastUsedLocation: String?
)