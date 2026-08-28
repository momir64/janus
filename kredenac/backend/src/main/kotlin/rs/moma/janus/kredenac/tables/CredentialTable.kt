package rs.moma.janus.kredenac.tables

import org.jetbrains.exposed.v1.core.Table

object CredentialTable : Table("credentials") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UserTable.id)
    val credentialId = binary("credential_id").uniqueIndex()
    val algorithm = varchar("algorithm", 16)
    val publicKey = binary("public_key")
    val signCount = long("sign_count")
    val integrityHash = varchar("integrity_hash", 64)
    override val primaryKey = PrimaryKey(id)
}
