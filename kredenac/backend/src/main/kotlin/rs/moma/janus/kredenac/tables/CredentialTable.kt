package rs.moma.janus.kredenac.tables

import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.core.Table
import kotlin.time.Clock

object CredentialTable : Table("credentials") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UserTable.id)
    val credentialId = binary("credential_id").uniqueIndex()
    val algorithm = varchar("algorithm", 16)
    val publicKey = binary("public_key")
    val signCount = long("sign_count")
    val aaguid = uuid("aaguid").nullable()
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val lastUsedAt = timestamp("last_used_at").nullable()
    val lastUsedIp = varchar("last_used_ip", 45).nullable()
    val lastUsedLocation = varchar("last_used_location", 128).nullable()
    val integrityHash = varchar("integrity_hash", 64)
    override val primaryKey = PrimaryKey(id)
}
