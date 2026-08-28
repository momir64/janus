package rs.moma.janus.kredenac.services

import rs.moma.janus.kredenac.repositories.NotesRepository
import rs.moma.janus.kredenac.crypto.algorithms.AesUtil
import rs.moma.janus.kredenac.common.NotFoundException
import rs.moma.janus.kredenac.repositories.StoredNote
import rs.moma.janus.kredenac.common.Owner
import rs.moma.janus.kredenac.dtos.NoteDto
import kotlin.uuid.Uuid

class NotesService(private val notesRepository: NotesRepository, private val userService: UserService) {
    context(owner: Owner)
    suspend fun list(): List<NoteDto> {
        val encryptionKey = userService.getEncryptionKey()
        return notesRepository.findAll().map { it.toDto(encryptionKey) }
    }

    context(owner: Owner)
    suspend fun create(title: String, content: String) {
        val encryptionKey = userService.getEncryptionKey()
        val title = AesUtil.encrypt(encryptionKey, title.toByteArray())
        val content = AesUtil.encrypt(encryptionKey, content.toByteArray())
        notesRepository.insert(title.ciphertext, title.iv, content.ciphertext, content.iv)
    }

    context(owner: Owner)
    suspend fun update(noteId: Uuid, title: String, content: String) {
        val encryptionKey = userService.getEncryptionKey()
        val title = AesUtil.encrypt(encryptionKey, title.toByteArray())
        val content = AesUtil.encrypt(encryptionKey, content.toByteArray())
        if (!notesRepository.update(noteId, title.ciphertext, title.iv, content.ciphertext, content.iv))
            throw NotFoundException("Note not found")
    }

    context(owner: Owner)
    suspend fun delete(noteId: Uuid) {
        if (!notesRepository.delete(noteId))
            throw NotFoundException("Note not found")
    }

    private fun StoredNote.toDto(encryptionKey: ByteArray): NoteDto {
        val title = String(AesUtil.decrypt(encryptionKey, encryptedTitle, encryptedTitleIv))
        val content = String(AesUtil.decrypt(encryptionKey, encryptedContent, encryptedContentIv))
        return NoteDto(id.toString(), title, content, updatedAt.toString())
    }
}
