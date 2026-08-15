package rs.moma.janus.kredenac.repository

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.moma.janus.kredenac.utils.UnauthorizedException
import rs.moma.janus.kredenac.crypto.algorithms.HmacUtil
import rs.moma.janus.kredenac.db.CredentialTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.uuid.Uuid

class StoredCredential(
    val id: Uuid,
    val userId: Uuid,
    val credentialId: ByteArray,
    val algorithm: String,
    val publicKey: ByteArray,
    val signCount: Long
)

class CredentialRepository(private val hmacSecret: ByteArray) {
    suspend fun insert(userId: Uuid, credentialId: ByteArray, algorithm: String, publicKey: ByteArray): Uuid = withContext(Dispatchers.IO) {
        val id = Uuid.random()
        val signCount = 0L

        transaction {
            CredentialTable.insert {
                it[CredentialTable.id] = id
                it[CredentialTable.userId] = userId
                it[CredentialTable.credentialId] = credentialId
                it[CredentialTable.algorithm] = algorithm
                it[CredentialTable.publicKey] = publicKey
                it[CredentialTable.signCount] = signCount
                it[integrityHash] = hashFor(id, userId, signCount, publicKey)
            }
        }
        id
    }

    suspend fun findByCredentialId(credentialId: ByteArray): StoredCredential? = withContext(Dispatchers.IO) {
        transaction {
            CredentialTable.selectAll()
                .where { CredentialTable.credentialId eq credentialId }
                .map { it.toStoredCredential() }
                .singleOrNull()
        }
    }

    suspend fun findAllForUser(userId: Uuid): List<StoredCredential> = withContext(Dispatchers.IO) {
        transaction {
            CredentialTable.selectAll()
                .where { CredentialTable.userId eq userId }
                .map { it.toStoredCredential() }
        }
    }

    suspend fun updateSignCount(stored: StoredCredential, newSignCount: Long) = withContext(Dispatchers.IO) {
        transaction {
            CredentialTable.update({ CredentialTable.id eq stored.id }) {
                it[signCount] = newSignCount
                it[integrityHash] = hashFor(stored.id, stored.userId, newSignCount, stored.publicKey)
            }
        }
    }

    private fun hashFor(id: Uuid, userId: Uuid, signCount: Long, publicKey: ByteArray): String {
        val idBytes = id.toString().toByteArray()
        val userIdBytes = userId.toString().toByteArray()
        val signCountBytes = ByteBuffer.allocate(8).putLong(signCount).array()
        return HmacUtil.hash(hmacSecret, idBytes + userIdBytes + signCountBytes + publicKey)
    }

    private fun ResultRow.toStoredCredential(): StoredCredential {
        val id = this[CredentialTable.id]
        val userId = this[CredentialTable.userId]
        val publicKey = this[CredentialTable.publicKey]
        val signCount = this[CredentialTable.signCount]

        if (hashFor(id, userId, signCount, publicKey) != this[CredentialTable.integrityHash])
            throw UnauthorizedException("Credential data failed integrity check")

        return StoredCredential(
            id, userId, this[CredentialTable.credentialId],
            this[CredentialTable.algorithm], publicKey, signCount
        )
    }
}
