package rs.moma.janus.kredenac.repository

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.moma.janus.kredenac.db.CredentialTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

class StoredCredential(
    val id: Uuid,
    val userId: Uuid,
    val credentialId: ByteArray,
    val publicKeyX: ByteArray,
    val publicKeyY: ByteArray,
    val signCount: Long
)

class CredentialRepository {
    suspend fun insert(userId: Uuid, credentialId: ByteArray, publicKeyX: ByteArray, publicKeyY: ByteArray) = withContext(Dispatchers.IO) {
        transaction {
            CredentialTable.insert {
                it[CredentialTable.userId] = userId
                it[CredentialTable.credentialId] = credentialId
                it[CredentialTable.publicKeyX] = publicKeyX
                it[CredentialTable.publicKeyY] = publicKeyY
                it[signCount] = 0
            }
        }
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

    suspend fun updateSignCount(id: Uuid, newSignCount: Long) = withContext(Dispatchers.IO) {
        transaction {
            CredentialTable.update({ CredentialTable.id eq id }) {
                it[signCount] = newSignCount
            }
        }
    }

    private fun ResultRow.toStoredCredential() = StoredCredential(
        this[CredentialTable.id],
        this[CredentialTable.userId],
        this[CredentialTable.credentialId],
        this[CredentialTable.publicKeyX],
        this[CredentialTable.publicKeyY],
        this[CredentialTable.signCount]
    )
}
