package rs.moma.janus.kredenac.tables

import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.core.Table
import kotlin.uuid.Uuid

object RefreshTokenTable : Table("refresh_tokens") {
    val id = uuid("id").clientDefault { Uuid.random() }
    val userId = uuid("user_id").references(UserTable.id)
    val credentialId = uuid("credential_id").references(CredentialTable.id)
    val chainId = uuid("chain_id").index()
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val expiresAt = timestamp("expires_at").index()
    val rotatedAt = timestamp("rotated_at").nullable()
    val integrityHash = varchar("integrity_hash", 64)
    override val primaryKey = PrimaryKey(id)

    init {
        index(false, userId, credentialId)
    }
}
