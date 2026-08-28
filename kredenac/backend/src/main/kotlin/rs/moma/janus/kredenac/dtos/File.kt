package rs.moma.janus.kredenac.dtos

import kotlinx.serialization.Serializable

@Serializable
data class FileDto(
    val id: String,
    val filename: String,
    val contentType: String,
    val size: Long,
    val createdAt: String
)
