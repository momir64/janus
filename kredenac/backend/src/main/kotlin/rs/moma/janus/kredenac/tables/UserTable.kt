package rs.moma.janus.kredenac.tables

import org.jetbrains.exposed.v1.core.Table

object UserTable : Table("users") {
    val id = uuid("id")
    val emailHash = varchar("email_hash", 64).uniqueIndex()
    val encryptedEmail = binary("encrypted_email")
    val encryptedEmailIv = binary("encrypted_email_iv")
    val encryptedUserKey = binary("encrypted_user_key")
    val encryptedUserKeyIv = binary("encrypted_user_key_iv")
    override val primaryKey = PrimaryKey(id)
}
