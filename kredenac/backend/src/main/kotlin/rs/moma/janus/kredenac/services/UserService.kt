package rs.moma.janus.kredenac.services

import rs.moma.janus.kredenac.repositories.RefreshTokenRepository
import rs.moma.janus.kredenac.repositories.FileContentRepository
import rs.moma.janus.kredenac.repositories.CredentialRepository
import rs.moma.janus.kredenac.repositories.FilesRepository
import rs.moma.janus.kredenac.repositories.NotesRepository
import rs.moma.janus.kredenac.repositories.UserRepository
import rs.moma.janus.kredenac.common.NotFoundException
import rs.moma.janus.kredenac.dtos.CredentialDto
import rs.moma.janus.kredenac.common.Owner
import kotlin.uuid.Uuid

class UserService(
    private val userRepository: UserRepository,
    private val credentialRepository: CredentialRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val notesRepository: NotesRepository,
    private val filesRepository: FilesRepository,
    private val fileContentRepository: FileContentRepository,
    private val emailService: EmailService
) {
    suspend fun register(email: String, credentialId: ByteArray, algorithm: String, publicKey: ByteArray) {
        val userId = userRepository.findIdByEmail(email) ?: userRepository.insert(email)
        credentialRepository.insert(userId, credentialId, algorithm, publicKey)
    }

    context(owner: Owner)
    suspend fun addCredential(credentialId: ByteArray, algorithm: String, publicKey: ByteArray) {
        credentialRepository.insert(owner.userId, credentialId, algorithm, publicKey)
    }

    context(owner: Owner)
    suspend fun listCredentials(): List<CredentialDto> =
        credentialRepository.findAll().map { CredentialDto(it.id.toString(), it.algorithm) }

    context(owner: Owner)
    suspend fun deleteCredential(credentialId: Uuid) {
        if (!credentialRepository.delete(credentialId)) throw NotFoundException("Credential not found")
        refreshTokenRepository.deleteChainsForCredential(credentialId)
    }

    suspend fun revokeCompromisedCredential(userId: Uuid, credentialId: Uuid) {
        try {
            context(Owner(userId)) {
                deleteCredential(credentialId)
            }
        } catch (_: NotFoundException) {
        }

        val email = userRepository.findEmailById(userId) ?: return
        emailService.send(
            to = email,
            subject = "Security alert: a passkey was disabled",
            html = """
                <p>We detected unusual activity from one of your passkeys and disabled it as a precaution.</p>
                <p>If you don't recognize this activity, we recommend reviewing your remaining passkeys and adding a new one from a trusted device.</p>
            """.trimIndent()
        )
    }

    context(owner: Owner)
    suspend fun deleteAccount() {
        refreshTokenRepository.deleteAllForUser()
        credentialRepository.deleteAll()

        filesRepository.findAll().forEach { fileContentRepository.delete(it.id) }
        filesRepository.deleteAll()

        notesRepository.deleteAll()
        userRepository.delete(owner.userId)
    }

    context(owner: Owner)
    suspend fun getEncryptionKey(): ByteArray =
        userRepository.encryptionKeyFor(owner.userId) ?: throw NotFoundException("User not found")
}
