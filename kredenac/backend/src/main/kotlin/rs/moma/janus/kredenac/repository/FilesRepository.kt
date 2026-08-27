package rs.moma.janus.kredenac.repository

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import rs.moma.janus.kredenac.db.FilesTable
import rs.moma.janus.kredenac.common.Owner
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.uuid.Uuid

class StoredFile(
    val id: Uuid,
    val userId: Uuid,
    val encryptedFilename: ByteArray,
    val encryptedFilenameIv: ByteArray,
    val encryptedContentIv: ByteArray,
    val contentType: String,
    val size: Long,
    val createdAt: Instant
)

class FilesRepository {
    context(owner: Owner)
    suspend fun insert(
        id: Uuid, encryptedFilename: ByteArray, encryptedFilenameIv: ByteArray, encryptedContentIv: ByteArray,
        contentType: String, size: Long
    ) = withContext(Dispatchers.IO) {
        transaction {
            FilesTable.insert {
                it[FilesTable.id] = id
                it[userId] = owner.userId
                it[FilesTable.encryptedFilename] = encryptedFilename
                it[FilesTable.encryptedFilenameIv] = encryptedFilenameIv
                it[FilesTable.encryptedContentIv] = encryptedContentIv
                it[FilesTable.contentType] = contentType
                it[FilesTable.size] = size
            }
        }
    }

    context(owner: Owner)
    suspend fun findAll(): List<StoredFile> = withContext(Dispatchers.IO) {
        transaction {
            FilesTable.selectAll()
                .where { FilesTable.userId eq owner.userId }
                .map { it.toStoredFile() }
        }
    }

    context(owner: Owner)
    suspend fun findById(id: Uuid): StoredFile? = withContext(Dispatchers.IO) {
        transaction {
            FilesTable.selectAll()
                .where { (FilesTable.id eq id) and (FilesTable.userId eq owner.userId) }
                .map { it.toStoredFile() }
                .singleOrNull()
        }
    }

    context(owner: Owner)
    suspend fun delete(id: Uuid): Boolean = withContext(Dispatchers.IO) {
        transaction {
            FilesTable.deleteWhere { (FilesTable.id eq id) and (FilesTable.userId eq owner.userId) } > 0
        }
    }

    context(owner: Owner)
    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        transaction {
            FilesTable.deleteWhere { FilesTable.userId eq owner.userId }
        }
    }

    private fun ResultRow.toStoredFile() = StoredFile(
        id = this[FilesTable.id],
        userId = this[FilesTable.userId],
        encryptedFilename = this[FilesTable.encryptedFilename],
        encryptedFilenameIv = this[FilesTable.encryptedFilenameIv],
        encryptedContentIv = this[FilesTable.encryptedContentIv],
        contentType = this[FilesTable.contentType],
        size = this[FilesTable.size],
        createdAt = this[FilesTable.createdAt]
    )
}
