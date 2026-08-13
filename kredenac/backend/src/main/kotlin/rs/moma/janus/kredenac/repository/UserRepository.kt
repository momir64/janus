package rs.moma.janus.kredenac.repository

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import rs.moma.janus.kredenac.db.UserTable
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

class StoredUser(
    val id: Uuid,
    val email: String,
    val wrappedNoteKey: ByteArray,
    val wrappedNoteKeyIv: ByteArray
)

class UserRepository {
    suspend fun insert(email: String, wrappedNoteKey: ByteArray, wrappedNoteKeyIv: ByteArray): Uuid = withContext(Dispatchers.IO) {
        transaction {
            UserTable.insert {
                it[UserTable.email] = email
                it[UserTable.wrappedNoteKey] = wrappedNoteKey
                it[UserTable.wrappedNoteKeyIv] = wrappedNoteKeyIv
            }[UserTable.id]
        }
    }

    suspend fun findById(id: Uuid): StoredUser? = withContext(Dispatchers.IO) {
        transaction {
            UserTable.selectAll()
                .where { UserTable.id eq id }
                .map { it.toStoredUser() }
                .singleOrNull()
        }
    }

    suspend fun findByEmail(email: String): StoredUser? = withContext(Dispatchers.IO) {
        transaction {
            UserTable.selectAll()
                .where { UserTable.email eq email }
                .map { it.toStoredUser() }
                .singleOrNull()
        }
    }

    private fun ResultRow.toStoredUser() = StoredUser(
        this[UserTable.id],
        this[UserTable.email],
        this[UserTable.wrappedNoteKey],
        this[UserTable.wrappedNoteKeyIv]
    )
}
