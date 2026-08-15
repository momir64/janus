package rs.moma.janus.kredenac.service

import rs.moma.janus.kredenac.crypto.algorithms.AesUtil
import rs.moma.janus.kredenac.repository.NotesRepository
import rs.moma.janus.kredenac.utils.NotFoundException
import rs.moma.janus.kredenac.repository.StoredNote
import rs.moma.janus.kredenac.model.NoteDto
import kotlin.uuid.Uuid

class NotesService(private val notesRepository: NotesRepository, private val userService: UserService) {
    suspend fun list(userId: Uuid): List<NoteDto> {
        val noteKey = userService.noteKeyFor(userId)
        return notesRepository.findAllForUser(userId).map { it.toDto(noteKey) }
    }

    suspend fun create(userId: Uuid, title: String, content: String): NoteDto {
        val noteKey = userService.noteKeyFor(userId)
        val encryptedTitle = AesUtil.encrypt(noteKey, title.toByteArray())
        val encryptedContent = AesUtil.encrypt(noteKey, content.toByteArray())

        val id = notesRepository.insert(
            userId,
            encryptedTitle.ciphertext,
            encryptedTitle.iv,
            encryptedContent.ciphertext,
            encryptedContent.iv
        )

        return notesRepository.findByIdForUser(id, userId)!!.toDto(noteKey)
    }

    suspend fun update(userId: Uuid, noteId: Uuid, title: String, content: String): NoteDto {
        notesRepository.findByIdForUser(noteId, userId) ?: throw NotFoundException("Note not found")
        val noteKey = userService.noteKeyFor(userId)
        val encryptedTitle = AesUtil.encrypt(noteKey, title.toByteArray())
        val encryptedContent = AesUtil.encrypt(noteKey, content.toByteArray())

        notesRepository.update(noteId, encryptedTitle.ciphertext, encryptedTitle.iv, encryptedContent.ciphertext, encryptedContent.iv)
        return notesRepository.findByIdForUser(noteId, userId)!!.toDto(noteKey)
    }

    suspend fun delete(userId: Uuid, noteId: Uuid) {
        notesRepository.findByIdForUser(noteId, userId) ?: throw NotFoundException("Note not found")
        notesRepository.deleteForUser(noteId, userId)
    }

    private fun StoredNote.toDto(noteKey: ByteArray): NoteDto {
        val title = String(AesUtil.decrypt(noteKey, encryptedTitle, encryptedTitleIv), Charsets.UTF_8)
        val content = String(AesUtil.decrypt(noteKey, encryptedContent, encryptedContentIv), Charsets.UTF_8)
        return NoteDto(id.toString(), title, content, updatedAt.toString())
    }
}
