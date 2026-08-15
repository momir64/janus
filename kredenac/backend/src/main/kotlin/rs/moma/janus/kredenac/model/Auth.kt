package rs.moma.janus.kredenac.model

import kotlin.io.encoding.Base64.PaddingOption.PRESENT_OPTIONAL
import kotlinx.serialization.Serializable
import kotlin.io.encoding.Base64

@JvmInline
@Serializable
value class Base64Url(val value: String) {
    fun decode(): ByteArray = Base64.UrlSafe.withPadding(PRESENT_OPTIONAL).decode(value)
}

@Serializable
data class RegisterFinishRequest(
    val email: String,
    val clientDataJSON: Base64Url,
    val attestationObject: Base64Url
)

@Serializable
data class LoginStartRequest(val email: String? = null)

@Serializable
data class LoginFinishRequest(
    val credentialId: Base64Url,
    val clientDataJSON: Base64Url,
    val authenticatorData: Base64Url,
    val signature: Base64Url
)

@Serializable
data class AddCredentialFinishRequest(
    val clientDataJSON: Base64Url,
    val attestationObject: Base64Url
)
