package rs.moma.privezak.security

import kotlinx.serialization.Serializable

@Serializable
data class Passkey(
    val id: String,
    val rpId: String,
    val rpName: String,
    val userHandle: String,
    val userName: String,
    val displayName: String,
    val signCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
