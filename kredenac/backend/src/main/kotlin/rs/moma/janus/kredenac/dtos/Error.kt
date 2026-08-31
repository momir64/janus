package rs.moma.janus.kredenac.dtos

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(val message: String, val code: String? = null)
