package rs.moma.janus.kredenac.repository

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.moma.janus.kredenac.common.UnauthorizedException
import rs.moma.janus.kredenac.crypto.algorithms.HmacUtil
import rs.moma.janus.kredenac.crypto.algorithms.AesUtil
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import rs.moma.janus.kredenac.db.UserTable
import javax.crypto.AEADBadTagException
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

class StoredUser(val id: Uuid, val email: String)

class UserRepository(
    private val hmacSecret: ByteArray,
    private val emailEncryptionKey: ByteArray,
    private val masterKey: ByteArray
) {
    suspend fun insert(email: String): Uuid = withContext(Dispatchers.IO) {
        val id = Uuid.random()
        val aad = id.toByteArray()
        val encryptedEmail = AesUtil.encrypt(emailEncryptionKey, email.toByteArray(), aad)
        val userKey = AesUtil.encrypt(masterKey, AesUtil.generateKey(), aad)

        transaction {
            UserTable.insert {
                it[UserTable.id] = id
                it[emailHash] = HmacUtil.hash(hmacSecret, email)
                it[this.encryptedEmail] = encryptedEmail.ciphertext
                it[encryptedEmailIv] = encryptedEmail.iv
                it[encryptedUserKey] = userKey.ciphertext
                it[encryptedUserKeyIv] = userKey.iv
            }
        }
        id
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
                .where { UserTable.emailHash eq HmacUtil.hash(hmacSecret, email) }
                .map { it.toStoredUser() }
                .singleOrNull()
        }
    }

    suspend fun noteKeyFor(id: Uuid): ByteArray? = withContext(Dispatchers.IO) {
        val row = transaction {
            UserTable.selectAll().where { UserTable.id eq id }.singleOrNull()
        } ?: return@withContext null
        val ciphertext = row[UserTable.encryptedUserKey]
        val iv = row[UserTable.encryptedUserKeyIv]
        val aad = id.toByteArray()
        try {
            AesUtil.decrypt(masterKey, ciphertext, iv, aad)
        } catch (_: AEADBadTagException) {
            throw UnauthorizedException("Note key failed integrity check")
        }
    }

    private fun ResultRow.toStoredUser(): StoredUser {
        val ciphertext = this[UserTable.encryptedEmail]
        val iv = this[UserTable.encryptedEmailIv]
        val id = this[UserTable.id]
        val aad = id.toByteArray()
        val email = try {
            String(AesUtil.decrypt(emailEncryptionKey, ciphertext, iv, aad))
        } catch (_: AEADBadTagException) {
            throw UnauthorizedException("User email failed integrity check")
        }
        return StoredUser(id, email)
    }
}
