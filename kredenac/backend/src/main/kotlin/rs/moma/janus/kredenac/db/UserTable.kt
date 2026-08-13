package rs.moma.janus.kredenac.db

import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.core.Table
import kotlin.time.Clock
import kotlin.uuid.Uuid

object UserTable : Table("users") {
    val id = uuid("id").clientDefault { Uuid.random() }
    val email = varchar("email", 255).uniqueIndex()
    val wrappedNoteKey = binary("wrapped_note_key")
    val wrappedNoteKeyIv = binary("wrapped_note_key_iv")
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    override val primaryKey = PrimaryKey(id)
}
