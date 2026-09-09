package rs.moma.janus.kredenac.tables

import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.core.Table
import kotlin.time.Clock

object FilesTable : Table("files") {
    val id = uuid("id")
    val userId = uuid("user_id").references(UserTable.id).index()
    val encryptedFilename = binary("encrypted_filename")
    val encryptedFilenameIv = binary("encrypted_filename_iv")
    val encryptedContentIv = binary("encrypted_content_iv")
    val size = long("size")
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    override val primaryKey = PrimaryKey(id)
}
