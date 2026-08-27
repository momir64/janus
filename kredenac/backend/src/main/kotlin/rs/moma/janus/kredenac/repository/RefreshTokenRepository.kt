package rs.moma.janus.kredenac.repository

import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.moma.janus.kredenac.common.CompromisedException
import rs.moma.janus.kredenac.crypto.algorithms.HmacUtil
import rs.moma.janus.kredenac.db.RefreshTokenTable
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import rs.moma.janus.kredenac.common.toByteArray
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.update
import rs.moma.janus.kredenac.common.Owner
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.time.Clock
import kotlin.uuid.Uuid

data class StoredRefreshToken(
    val id: Uuid,
    val userId: Uuid,
    val credentialId: Uuid,
    val chainId: Uuid,
    val tokenHash: String,
    val expiresAt: Instant,
    val rotatedAt: Instant?
)

class RefreshTokenRepository(private val hmacSecret: ByteArray) {
    suspend fun insert(
        userId: Uuid, credentialId: Uuid, chainId: Uuid,
        tokenHash: String, expiresAt: Instant
    ) = withContext(Dispatchers.IO) {
        val id = Uuid.random()
        transaction {
            RefreshTokenTable.insert {
                it[RefreshTokenTable.id] = id
                it[RefreshTokenTable.userId] = userId
                it[RefreshTokenTable.credentialId] = credentialId
                it[RefreshTokenTable.chainId] = chainId
                it[RefreshTokenTable.tokenHash] = tokenHash
                it[RefreshTokenTable.expiresAt] = expiresAt
                it[integrityHash] = hashFor(id, userId, credentialId, chainId, tokenHash, expiresAt, null)
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
            RefreshTokenTable.update({
                (RefreshTokenTable.id eq stored.id) and (RefreshTokenTable.userId eq stored.userId)
            }) {
                it[RefreshTokenTable.rotatedAt] = rotatedAt
                it[integrityHash] = hashFor(
                    stored.id, stored.userId, stored.credentialId,
                    stored.chainId, stored.tokenHash, stored.expiresAt, rotatedAt
                )
            }
        }
    }

    suspend fun deleteChain(chainId: Uuid) = withContext(Dispatchers.IO) {
        transaction {
            RefreshTokenTable.deleteWhere { RefreshTokenTable.chainId eq chainId }
        }
    }

    context(owner: Owner)
    suspend fun deleteChainsForCredential(credentialId: Uuid) = withContext(Dispatchers.IO) {
        transaction {
            RefreshTokenTable.deleteWhere {
                (RefreshTokenTable.userId eq owner.userId) and (RefreshTokenTable.credentialId eq credentialId)
            }
        }
    }

    context(owner: Owner)
    suspend fun deleteAllForUser() = withContext(Dispatchers.IO) {
        transaction {
            RefreshTokenTable.deleteWhere { RefreshTokenTable.userId eq owner.userId }
        }
    }

    suspend fun deleteExpired() = withContext(Dispatchers.IO) {
        transaction {
            RefreshTokenTable.deleteWhere { expiresAt less Clock.System.now() }
        }
    }

    private fun hashFor(
        id: Uuid, userId: Uuid, credentialId: Uuid, chainId: Uuid,
        tokenHash: String, expiresAt: Instant, rotatedAt: Instant?
    ): String = hashFor(StoredRefreshToken(id, userId, credentialId, chainId, tokenHash, expiresAt, rotatedAt))

    private fun hashFor(refreshToken: StoredRefreshToken): String {
        return HmacUtil.hash(
            hmacSecret,
            refreshToken.id.toByteArray() +
                    refreshToken.userId.toByteArray() +
                    refreshToken.credentialId.toByteArray() +
                    refreshToken.chainId.toByteArray() +
                    refreshToken.tokenHash.toByteArray() +
                    refreshToken.expiresAt.epochSeconds.toByteArray() +
                    (refreshToken.rotatedAt?.epochSeconds?.toByteArray() ?: byteArrayOf())
        )
    }

    private fun ResultRow.toStoredRefreshToken(): StoredRefreshToken {
        val token = StoredRefreshToken(
            id = this[RefreshTokenTable.id],
            userId = this[RefreshTokenTable.userId],
            credentialId = this[RefreshTokenTable.credentialId],
            chainId = this[RefreshTokenTable.chainId],
            tokenHash = this[RefreshTokenTable.tokenHash],
            expiresAt = this[RefreshTokenTable.expiresAt],
            rotatedAt = this[RefreshTokenTable.rotatedAt]
        )

        if (hashFor(token) != this[RefreshTokenTable.integrityHash])
            throw CompromisedException("Refresh token (id=${token.id}) for user=${token.userId} failed integrity check")

        return token
    }
}
