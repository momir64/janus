package rs.moma.janus.kredenac.repository

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.moma.janus.kredenac.utils.UnauthorizedException
import rs.moma.janus.kredenac.crypto.algorithms.HmacUtil
import rs.moma.janus.kredenac.db.RefreshTokenTable
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import kotlin.time.Instant
import kotlin.time.Clock
import kotlin.uuid.Uuid

data class StoredRefreshToken(
    val id: Uuid,
    val userId: Uuid,
    val chainId: Uuid,
    val tokenHash: String,
    val expiresAt: Instant,
    val rotatedAt: Instant?
)

class RefreshTokenRepository(private val hmacSecret: ByteArray) {
    suspend fun insert(userId: Uuid, chainId: Uuid, tokenHash: String, expiresAt: Instant) = withContext(Dispatchers.IO) {
        val id = Uuid.random()
        transaction {
            RefreshTokenTable.insert {
                it[RefreshTokenTable.id] = id
                it[RefreshTokenTable.userId] = userId
                it[RefreshTokenTable.chainId] = chainId
                it[RefreshTokenTable.tokenHash] = tokenHash
                it[RefreshTokenTable.expiresAt] = expiresAt
                it[integrityHash] = hashFor(id, userId, chainId, tokenHash, expiresAt, null)
            }
        }
    }

    suspend fun findByHash(tokenHash: String): StoredRefreshToken? = withContext(Dispatchers.IO) {
        transaction {
            RefreshTokenTable.selectAll()
                .where { RefreshTokenTable.tokenHash eq tokenHash }
                .map { it.toStoredRefreshToken() }
                .singleOrNull()
        }
    }

    suspend fun markRotated(stored: StoredRefreshToken) = withContext(Dispatchers.IO) {
        val rotatedAt = Clock.System.now()
        transaction {
            RefreshTokenTable.update({ RefreshTokenTable.id eq stored.id }) {
                it[RefreshTokenTable.rotatedAt] = rotatedAt
                it[integrityHash] = hashFor(stored.id, stored.userId, stored.chainId, stored.tokenHash, stored.expiresAt, rotatedAt)
            }
        }
    }

    suspend fun deleteChain(chainId: Uuid) = withContext(Dispatchers.IO) {
        transaction {
            RefreshTokenTable.deleteWhere { RefreshTokenTable.chainId eq chainId }
        }
    }

    suspend fun deleteExpired() = withContext(Dispatchers.IO) {
        transaction {
            RefreshTokenTable.deleteWhere { expiresAt less Clock.System.now() }
        }
    }

    private fun hashFor(id: Uuid, userId: Uuid, chainId: Uuid, tokenHash: String, expiresAt: Instant, rotatedAt: Instant?): String {
        val idBytes = id.toString().toByteArray()
        val userIdBytes = userId.toString().toByteArray()
        val chainIdBytes = chainId.toString().toByteArray()
        val tokenHashBytes = tokenHash.toByteArray()
        val expiresAtBytes = ByteBuffer.allocate(8).putLong(expiresAt.epochSeconds).array()
        val rotatedAtFlag = byteArrayOf(if (rotatedAt != null) 1 else 0)
        val rotatedAtBytes = ByteBuffer.allocate(8).putLong(rotatedAt?.epochSeconds ?: 0L).array()
        return HmacUtil.hash(hmacSecret, idBytes + userIdBytes + chainIdBytes + tokenHashBytes + expiresAtBytes + rotatedAtFlag + rotatedAtBytes)
    }

    private fun ResultRow.toStoredRefreshToken(): StoredRefreshToken {
        val id = this[RefreshTokenTable.id]
        val userId = this[RefreshTokenTable.userId]
        val chainId = this[RefreshTokenTable.chainId]
        val tokenHash = this[RefreshTokenTable.tokenHash]
        val expiresAt = this[RefreshTokenTable.expiresAt]
        val rotatedAt = this[RefreshTokenTable.rotatedAt]

        if (hashFor(id, userId, chainId, tokenHash, expiresAt, rotatedAt) != this[RefreshTokenTable.integrityHash])
            throw UnauthorizedException("Refresh token data failed integrity check")

        return StoredRefreshToken(id, userId, chainId, tokenHash, expiresAt, rotatedAt)
    }
}
