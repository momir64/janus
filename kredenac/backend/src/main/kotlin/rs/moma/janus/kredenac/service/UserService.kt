package rs.moma.janus.kredenac.service

import rs.moma.janus.kredenac.repository.CredentialRepository
import rs.moma.janus.kredenac.crypto.algorithms.VerifyUtil
import rs.moma.janus.kredenac.repository.UserRepository
import rs.moma.janus.kredenac.utils.ConflictException
import rs.moma.janus.kredenac.utils.NotFoundException
import rs.moma.janus.kredenac.repository.StoredUser
import kotlin.uuid.Uuid

class UserService(
    private val userRepository: UserRepository,
    private val credentialRepository: CredentialRepository
) {
    suspend fun createUser(email: String, credentialId: ByteArray, algorithm: VerifyUtil, publicKey: ByteArray): Uuid {
        if (userRepository.findByEmail(email) != null)
            throw ConflictException("Email already registered")

        val userId = userRepository.insert(email)
        credentialRepository.insert(userId, credentialId, algorithm.algorithm, publicKey)
        return userId
    }

    suspend fun addCredential(userId: Uuid, credentialId: ByteArray, algorithm: VerifyUtil, publicKey: ByteArray) {
        getById(userId)
        credentialRepository.insert(userId, credentialId, algorithm.algorithm, publicKey)
    }

    suspend fun getById(userId: Uuid): StoredUser = userRepository.findById(userId) ?: throw NotFoundException("User not found")

    suspend fun noteKeyFor(userId: Uuid): ByteArray =
        userRepository.noteKeyFor(userId) ?: throw NotFoundException("User not found")
}
