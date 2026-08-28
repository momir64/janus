package rs.moma.janus.kredenac.tables

import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.core.Table
import kotlin.time.Clock
import kotlin.uuid.Uuid

object NotesTable : Table("notes") {
    val id = uuid("id").clientDefault { Uuid.random() }
    val userId = uuid("user_id").references(UserTable.id)
    val encryptedTitle = binary("encrypted_title")
    val encryptedTitleIv = binary("encrypted_title_iv")
    val encryptedContent = binary("encrypted_content")
    val encryptedContentIv = binary("encrypted_content_iv")
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    val updatedAt = timestamp("updated_at").clientDefault { Clock.System.now() }
    override val primaryKey = PrimaryKey(id)
}
