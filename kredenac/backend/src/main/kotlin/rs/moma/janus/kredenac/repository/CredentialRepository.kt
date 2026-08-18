package rs.moma.janus.kredenac.repository

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.moma.janus.kredenac.common.CompromisedException
import rs.moma.janus.kredenac.crypto.algorithms.HmacUtil
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import rs.moma.janus.kredenac.common.toByteArray
import rs.moma.janus.kredenac.db.CredentialTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import rs.moma.janus.kredenac.common.Owner
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    suspend fun insert(
        userId: Uuid, credentialId: ByteArray,
        algorithm: String, publicKey: ByteArray
    ): Uuid = withContext(Dispatchers.IO) {
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

    context(owner: Owner)
    suspend fun findAll(): List<StoredCredential> = withContext(Dispatchers.IO) {
        transaction {
            CredentialTable.selectAll()
                .where { CredentialTable.userId eq owner.userId }
                .map { it.toStoredCredential() }
        }
    }

    suspend fun updateSignCount(stored: StoredCredential, newSignCount: Long) = withContext(Dispatchers.IO) {
        transaction {
            CredentialTable.update({ (CredentialTable.id eq stored.id) and (CredentialTable.userId eq stored.userId) }) {
                it[signCount] = newSignCount
                it[integrityHash] = hashFor(stored.id, stored.userId, newSignCount, stored.publicKey)
            }
        }
    }

    context(owner: Owner)
    suspend fun delete(id: Uuid): Boolean = withContext(Dispatchers.IO) {
        transaction {
            CredentialTable.deleteWhere { (CredentialTable.id eq id) and (CredentialTable.userId eq owner.userId) } > 0
        }
    }

    private fun hashFor(id: Uuid, userId: Uuid, signCount: Long, publicKey: ByteArray): String {
        return HmacUtil.hash(hmacSecret, id.toByteArray() + userId.toByteArray() + signCount.toByteArray() + publicKey)
    }

    private fun ResultRow.toStoredCredential(): StoredCredential {
        val id = this[CredentialTable.id]
        val userId = this[CredentialTable.userId]
        val publicKey = this[CredentialTable.publicKey]
        val signCount = this[CredentialTable.signCount]

        if (hashFor(id, userId, signCount, publicKey) != this[CredentialTable.integrityHash])
            throw CompromisedException("Credential data (id=$id) for user=$userId failed integrity check")

        return StoredCredential(
            id, userId, this[CredentialTable.credentialId],
            this[CredentialTable.algorithm], publicKey, signCount
        )
    }
}