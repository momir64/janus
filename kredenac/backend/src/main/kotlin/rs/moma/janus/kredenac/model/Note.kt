package rs.moma.janus.kredenac.model

import kotlinx.serialization.Serializable

@Serializable
data class NoteDto(
    val id: String,
    val title: String,
    val content: String,
    val updatedAt: String
)

@Serializable
data class CreateNoteRequest(
    val title: String,
    val content: String
)

@Serializable
data class UpdateNoteRequest(
    val title: String,
    val content: String
)
