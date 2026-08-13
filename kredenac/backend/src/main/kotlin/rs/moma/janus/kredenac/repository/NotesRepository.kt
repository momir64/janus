package rs.moma.janus.kredenac.repository

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import rs.moma.janus.kredenac.db.NotesTable
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.time.Clock
import kotlin.uuid.Uuid

class StoredNote(
    val id: Uuid,
    val userId: Uuid,
    val encryptedTitle: ByteArray,
    val encryptedTitleIv: ByteArray,
    val encryptedContent: ByteArray,
    val encryptedContentIv: ByteArray,
    val updatedAt: Instant
)

class NotesRepository {
    suspend fun insert(
        userId: Uuid, encryptedTitle: ByteArray, encryptedTitleIv: ByteArray,
        encryptedContent: ByteArray, encryptedContentIv: ByteArray
    ): Uuid = withContext(Dispatchers.IO) {
        transaction {
            NotesTable.insert {
                it[NotesTable.userId] = userId
                it[NotesTable.encryptedTitle] = encryptedTitle
                it[NotesTable.encryptedTitleIv] = encryptedTitleIv
                it[NotesTable.encryptedContent] = encryptedContent
                it[NotesTable.encryptedContentIv] = encryptedContentIv
            }[NotesTable.id]
        }
    }

    suspend fun findAllForUser(userId: Uuid): List<StoredNote> = withContext(Dispatchers.IO) {
        transaction {
            NotesTable.selectAll()
                .where { NotesTable.userId eq userId }
                .map { it.toStoredNote() }
        }
    }

    suspend fun findByIdForUser(id: Uuid, userId: Uuid): StoredNote? = withContext(Dispatchers.IO) {
        transaction {
            NotesTable.selectAll()
                .where { (NotesTable.id eq id) and (NotesTable.userId eq userId) }
                .map { it.toStoredNote() }
                .singleOrNull()
        }
    }

    suspend fun update(
        id: Uuid, encryptedTitle: ByteArray, encryptedTitleIv: ByteArray,
        encryptedContent: ByteArray, encryptedContentIv: ByteArray
    ) = withContext(Dispatchers.IO) {
        transaction {
            NotesTable.update({ NotesTable.id eq id }) {
                it[NotesTable.encryptedTitle] = encryptedTitle
                it[NotesTable.encryptedTitleIv] = encryptedTitleIv
                it[NotesTable.encryptedContent] = encryptedContent
                it[NotesTable.encryptedContentIv] = encryptedContentIv
                it[updatedAt] = Clock.System.now()
            }
        }
    }

    suspend fun deleteForUser(id: Uuid, userId: Uuid) = withContext(Dispatchers.IO) {
        transaction {
            NotesTable.deleteWhere { (NotesTable.id eq id) and (NotesTable.userId eq userId) }
        }
    }

    private fun ResultRow.toStoredNote() = StoredNote(
        this[NotesTable.id],
        this[NotesTable.userId],
        this[NotesTable.encryptedTitle],
        this[NotesTable.encryptedTitleIv],
        this[NotesTable.encryptedContent],
        this[NotesTable.encryptedContentIv],
        this[NotesTable.updatedAt]
    )
}
