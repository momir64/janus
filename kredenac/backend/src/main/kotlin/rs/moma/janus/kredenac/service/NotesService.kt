package rs.moma.janus.kredenac.service

import rs.moma.janus.kredenac.repository.NotesRepository
import rs.moma.janus.kredenac.crypto.algorithms.AesUtil
import rs.moma.janus.kredenac.common.NotFoundException
import rs.moma.janus.kredenac.repository.StoredNote
import rs.moma.janus.kredenac.model.NoteDto
import rs.moma.janus.kredenac.common.Owner
import kotlin.uuid.Uuid

class NotesService(private val notesRepository: NotesRepository, private val userService: UserService) {
    context(owner: Owner)
    suspend fun list(): List<NoteDto> {
        val noteKey = userService.noteKeyFor()
        return notesRepository.findAll().map { it.toDto(noteKey) }
    }

    context(owner: Owner)
    suspend fun create(title: String, content: String): NoteDto {
        val noteKey = userService.noteKeyFor()
        val title = AesUtil.encrypt(noteKey, title.toByteArray())
        val content = AesUtil.encrypt(noteKey, content.toByteArray())
        val id = notesRepository.insert(title.ciphertext, title.iv, content.ciphertext, content.iv)
        return notesRepository.findById(id)!!.toDto(noteKey)
    }

    context(owner: Owner)
    suspend fun update(noteId: Uuid, title: String, content: String): NoteDto {
        notesRepository.findById(noteId) ?: throw NotFoundException("Note not found")
        val noteKey = userService.noteKeyFor()
        val title = AesUtil.encrypt(noteKey, title.toByteArray())
        val content = AesUtil.encrypt(noteKey, content.toByteArray())
        notesRepository.update(noteId, title.ciphertext, title.iv, content.ciphertext, content.iv)
        return notesRepository.findById(noteId)!!.toDto(noteKey)
    }

    context(owner: Owner)
    suspend fun delete(noteId: Uuid) {
        notesRepository.findById(noteId) ?: throw NotFoundException("Note not found")
        notesRepository.delete(noteId)
    }

    private fun StoredNote.toDto(noteKey: ByteArray): NoteDto {
        val title = String(AesUtil.decrypt(noteKey, encryptedTitle, encryptedTitleIv), Charsets.UTF_8)
        val content = String(AesUtil.decrypt(noteKey, encryptedContent, encryptedContentIv), Charsets.UTF_8)
        return NoteDto(id.toString(), title, content, updatedAt.toString())
    }
}
