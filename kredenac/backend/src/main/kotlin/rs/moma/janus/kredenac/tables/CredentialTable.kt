package rs.moma.janus.kredenac.tables

import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.core.Table

object CredentialTable : Table("credentials") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UserTable.id).index()
    val credentialId = binary("credential_id").uniqueIndex()
    val algorithm = varchar("algorithm", 16)
    val publicKey = binary("public_key")
    val signCount = long("sign_count")
    val aaguid = uuid("aaguid").nullable()
    val createdAt = timestamp("created_at")
    val lastUsedAt = timestamp("last_used_at").nullable()
    val encryptedLastUsedIp = binary("encrypted_last_used_ip").nullable()
    val encryptedLastUsedIpIv = binary("encrypted_last_used_ip_iv").nullable()
    val encryptedLastUsedLocation = binary("encrypted_last_used_location").nullable()
    val encryptedLastUsedLocationIv = binary("encrypted_last_used_location_iv").nullable()
    val integrityHash = varchar("integrity_hash", 64)
    override val primaryKey = PrimaryKey(id)
}
