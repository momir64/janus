package rs.moma.janus.kredenac.repository

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.moma.janus.kredenac.db.RefreshTokenTable
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.time.Clock
import kotlin.uuid.Uuid

data class StoredRefreshToken(
    val id: Uuid,
    val userId: Uuid,
    val chainId: Uuid,
    val tokenHash: String,
    val expiresAt: Instant,
    val revokedAt: Instant?,
    val rotatedAt: Instant?
)

class RefreshTokenRepository {
    suspend fun insert(userId: Uuid, chainId: Uuid, tokenHash: String, expiresAt: Instant) = withContext(Dispatchers.IO) {
        transaction {
            RefreshTokenTable.insert {
                it[RefreshTokenTable.userId] = userId
                it[RefreshTokenTable.chainId] = chainId
                it[RefreshTokenTable.tokenHash] = tokenHash
                it[issuedAt] = Clock.System.now()
                it[RefreshTokenTable.expiresAt] = expiresAt
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

    suspend fun markRotated(id: Uuid) = withContext(Dispatchers.IO) {
        transaction {
            RefreshTokenTable.update({ RefreshTokenTable.id eq id }) {
                it[rotatedAt] = Clock.System.now()
            }
        }
    }

    suspend fun revokeChain(chainId: Uuid) = withContext(Dispatchers.IO) {
        transaction {
            RefreshTokenTable.update({ RefreshTokenTable.chainId eq chainId }) {
                it[revokedAt] = Clock.System.now()
            }
        }
    }

    private fun ResultRow.toStoredRefreshToken() = StoredRefreshToken(
        this[RefreshTokenTable.id],
        this[RefreshTokenTable.userId],
        this[RefreshTokenTable.chainId],
        this[RefreshTokenTable.tokenHash],
        this[RefreshTokenTable.expiresAt],
        this[RefreshTokenTable.revokedAt],
        this[RefreshTokenTable.rotatedAt]
    )
}
