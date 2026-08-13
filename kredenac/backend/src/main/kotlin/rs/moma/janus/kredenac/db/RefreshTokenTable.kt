package rs.moma.janus.kredenac.db

import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.core.Table
import kotlin.uuid.Uuid

object RefreshTokenTable : Table("refresh_tokens") {
    val id = uuid("id").clientDefault { Uuid.random() }
    val userId = uuid("user_id").references(UserTable.id)
    val chainId = uuid("chain_id")
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val issuedAt = timestamp("issued_at")
    val expiresAt = timestamp("expires_at")
    val revokedAt = timestamp("revoked_at").nullable()
    val rotatedAt = timestamp("rotated_at").nullable()
    override val primaryKey = PrimaryKey(id)
}
