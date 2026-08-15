package rs.moma.janus.kredenac.db

import org.jetbrains.exposed.v1.core.Table

object UserTable : Table("users") {
    val id = uuid("id")
    val emailHash = varchar("email_hash", 64).uniqueIndex()
    val emailEncrypted = binary("email_encrypted")
    val emailEncryptedIv = binary("email_encrypted_iv")
    val wrappedNoteKey = binary("wrapped_note_key")
    val wrappedNoteKeyIv = binary("wrapped_note_key_iv")
    override val primaryKey = PrimaryKey(id)
}
