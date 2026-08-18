package rs.moma.janus.kredenac.repository

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import rs.moma.janus.kredenac.db.NotesTable
import rs.moma.janus.kredenac.common.Owner
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
    context(owner: Owner)
    suspend fun insert(
        encryptedTitle: ByteArray, encryptedTitleIv: ByteArray,
        encryptedContent: ByteArray, encryptedContentIv: ByteArray
    ): Uuid = withContext(Dispatchers.IO) {
        transaction {
            NotesTable.insert {
                it[userId] = owner.userId
                it[NotesTable.encryptedTitle] = encryptedTitle
                it[NotesTable.encryptedTitleIv] = encryptedTitleIv
                it[NotesTable.encryptedContent] = encryptedContent
                it[NotesTable.encryptedContentIv] = encryptedContentIv
            }[NotesTable.id]
        }
    }

    context(owner: Owner)
    suspend fun findAll(): List<StoredNote> = withContext(Dispatchers.IO) {
        transaction {
            NotesTable.selectAll()
                .where { NotesTable.userId eq owner.userId }
                .map { it.toStoredNote() }
        }
    }

    context(owner: Owner)
    suspend fun update(
        id: Uuid, encryptedTitle: ByteArray, encryptedTitleIv: ByteArray,
        encryptedContent: ByteArray, encryptedContentIv: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        transaction {
            NotesTable.update({ (NotesTable.id eq id) and (NotesTable.userId eq owner.userId) }) {
                it[NotesTable.encryptedTitle] = encryptedTitle
                it[NotesTable.encryptedTitleIv] = encryptedTitleIv
                it[NotesTable.encryptedContent] = encryptedContent
                it[NotesTable.encryptedContentIv] = encryptedContentIv
                it[updatedAt] = Clock.System.now()
            } > 0
        }
    }

    context(owner: Owner)
    suspend fun delete(id: Uuid): Boolean = withContext(Dispatchers.IO) {
        transaction {
            NotesTable.deleteWhere { (NotesTable.id eq id) and (NotesTable.userId eq owner.userId) } > 0
        }
    }

    private fun ResultRow.toStoredNote() = StoredNote(
        id = this[NotesTable.id],
        userId = this[NotesTable.userId],
        encryptedTitle = this[NotesTable.encryptedTitle],
        encryptedTitleIv = this[NotesTable.encryptedTitleIv],
        encryptedContent = this[NotesTable.encryptedContent],
        encryptedContentIv = this[NotesTable.encryptedContentIv],
        updatedAt = this[NotesTable.updatedAt]
    )
}