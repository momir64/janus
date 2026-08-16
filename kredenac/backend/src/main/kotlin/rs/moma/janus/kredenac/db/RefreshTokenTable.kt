package rs.moma.janus.kredenac.db

import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.core.Table
import kotlin.uuid.Uuid

object RefreshTokenTable : Table("refresh_tokens") {
    val id = uuid("id").clientDefault { Uuid.random() }
    val userId = uuid("user_id").references(UserTable.id)
    val credentialId = uuid("credential_id").references(CredentialTable.id)
    val chainId = uuid("chain_id")
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val expiresAt = timestamp("expires_at")
    val rotatedAt = timestamp("rotated_at").nullable()
    val integrityHash = varchar("integrity_hash", 64)
    override val primaryKey = PrimaryKey(id)
}
