package rs.moma.janus.kredenac.db

import org.jetbrains.exposed.v1.datetime.timestamp
import org.jetbrains.exposed.v1.core.Table
import kotlin.time.Clock
import kotlin.uuid.Uuid

object CredentialTable : Table("credentials") {
    val id = uuid("id").clientDefault { Uuid.random() }
    val userId = uuid("user_id").references(UserTable.id)
    val credentialId = binary("credential_id").uniqueIndex()
    val publicKeyX = binary("public_key_x")
    val publicKeyY = binary("public_key_y")
    val signCount = long("sign_count")
    val createdAt = timestamp("created_at").clientDefault { Clock.System.now() }
    override val primaryKey = PrimaryKey(id)
}
