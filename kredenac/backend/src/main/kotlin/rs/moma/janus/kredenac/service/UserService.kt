package rs.moma.janus.kredenac.service

import rs.moma.janus.kredenac.repository.CredentialRepository
import rs.moma.janus.kredenac.repository.UserRepository
import rs.moma.janus.kredenac.utils.ConflictException
import rs.moma.janus.kredenac.utils.NotFoundException
import rs.moma.janus.kredenac.repository.StoredUser
import kotlin.uuid.Uuid

class UserService(
    private val userRepository: UserRepository,
    private val credentialRepository: CredentialRepository,
    private val masterKey: ByteArray
) {
    suspend fun createUser(email: String, credentialId: ByteArray, publicKeyX: ByteArray, publicKeyY: ByteArray): Uuid {
        if (userRepository.findByEmail(email) != null)
            throw ConflictException("Email already registered")

        val noteKey = CryptoService.generateKey()
        val wrapped = CryptoService.encrypt(masterKey, noteKey)

        val userId = userRepository.insert(email, wrapped.ciphertext, wrapped.iv)
        credentialRepository.insert(userId, credentialId, publicKeyX, publicKeyY)
        return userId
    }

    suspend fun getById(userId: Uuid): StoredUser = userRepository.findById(userId) ?: throw NotFoundException("User not found")

    suspend fun noteKeyFor(userId: Uuid): ByteArray {
        val user = getById(userId)
        return CryptoService.decrypt(masterKey, user.wrappedNoteKey, user.wrappedNoteKeyIv)
    }
}
