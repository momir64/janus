package rs.moma.janus.kredenac.repositories

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.moma.janus.kredenac.common.CompromisedException
import rs.moma.janus.kredenac.crypto.algorithms.HmacUtil
import rs.moma.janus.kredenac.crypto.algorithms.AesUtil
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import rs.moma.janus.kredenac.tables.UserTable
import org.jetbrains.exposed.v1.jdbc.insert
import javax.crypto.AEADBadTagException
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.uuid.Uuid

class UserRepository(
    private val hmacSecret: ByteArray,
    private val piiEncryptionKey: ByteArray,
    private val masterKey: ByteArray
) {
    suspend fun insert(email: String): Uuid = withContext(Dispatchers.IO) {
        val id = Uuid.random()
        val aad = id.toByteArray()
        val encryptedEmail = AesUtil.encrypt(piiEncryptionKey, email.toByteArray(), aad)
        val encryptedUserKey = AesUtil.encrypt(masterKey, AesUtil.generateKey(), aad)

        transaction {
            UserTable.insert {
                it[UserTable.id] = id
                it[emailHash] = HmacUtil.hash(hmacSecret, email)
                it[this.encryptedEmail] = encryptedEmail.ciphertext
                it[encryptedEmailIv] = encryptedEmail.iv
                it[this.encryptedUserKey] = encryptedUserKey.ciphertext
                it[encryptedUserKeyIv] = encryptedUserKey.iv
            }
        }
        id
    }

    suspend fun findIdByEmail(email: String): Uuid? = withContext(Dispatchers.IO) {
        transaction {
            UserTable.selectAll()
                .where { UserTable.emailHash eq HmacUtil.hash(hmacSecret, email) }
                .map { it[UserTable.id] }
                .singleOrNull()
        }
    }

    suspend fun encryptionKeyFor(id: Uuid): ByteArray? = withContext(Dispatchers.IO) {
        val row = transaction {
            UserTable.selectAll().where { UserTable.id eq id }.singleOrNull()
        } ?: return@withContext null

        val ciphertext = row[UserTable.encryptedUserKey]
        val iv = row[UserTable.encryptedUserKeyIv]
        try {
            AesUtil.decrypt(masterKey, ciphertext, iv, id.toByteArray())
        } catch (_: AEADBadTagException) {
            throw CompromisedException("Encryption key for user=$id failed integrity check")
        }
    }

    suspend fun findEmailById(id: Uuid): String? = withContext(Dispatchers.IO) {
        val row = transaction {
            UserTable.selectAll().where { UserTable.id eq id }.singleOrNull()
        } ?: return@withContext null

        val ciphertext = row[UserTable.encryptedEmail]
        val iv = row[UserTable.encryptedEmailIv]
        try {
            String(AesUtil.decrypt(piiEncryptionKey, ciphertext, iv, id.toByteArray()))
        } catch (_: AEADBadTagException) {
            throw CompromisedException("Email for user=$id failed integrity check")
        }
    }

    suspend fun delete(id: Uuid) = withContext(Dispatchers.IO) {
        transaction {
            UserTable.deleteWhere { UserTable.id eq id }
        }
    }
}
