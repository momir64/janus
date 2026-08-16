package rs.moma.janus.kredenac.service

import rs.moma.janus.kredenac.repository.CredentialRepository
import rs.moma.janus.kredenac.repository.UserRepository
import rs.moma.janus.kredenac.utils.ConflictException
import rs.moma.janus.kredenac.utils.NotFoundException
import kotlin.uuid.Uuid

class UserService(
    private val userRepository: UserRepository,
    private val credentialRepository: CredentialRepository
) {
    suspend fun createUser(email: String, credentialId: ByteArray, algorithm: String, publicKey: ByteArray): Uuid {
        if (userRepository.findByEmail(email) != null)
            throw ConflictException("Email already registered")

        val userId = userRepository.insert(email)
        credentialRepository.insert(userId, credentialId, algorithm, publicKey)
        return userId
    }

    suspend fun addCredential(userId: Uuid, credentialId: ByteArray, algorithm: String, publicKey: ByteArray) {
        credentialRepository.insert(userId, credentialId, algorithm, publicKey)
    }

    suspend fun noteKeyFor(userId: Uuid): ByteArray =
        userRepository.noteKeyFor(userId) ?: throw NotFoundException("User not found")
}
