package rs.moma.janus.kredenac.service

import rs.moma.janus.kredenac.repository.RefreshTokenRepository
import rs.moma.janus.kredenac.repository.CredentialRepository
import rs.moma.janus.kredenac.repository.UserRepository
import rs.moma.janus.kredenac.common.NotFoundException
import rs.moma.janus.kredenac.model.CredentialDto
import rs.moma.janus.kredenac.common.Owner
import kotlin.uuid.Uuid

class UserService(
    private val userRepository: UserRepository,
    private val credentialRepository: CredentialRepository,
    private val refreshTokenRepository: RefreshTokenRepository
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

    context(owner: Owner)
    suspend fun getEncryptionKey(): ByteArray =
        userRepository.encryptionKeyFor(owner.userId) ?: throw NotFoundException("User not found")
}