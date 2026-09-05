package rs.moma.janus.kredenac.common

import kotlin.uuid.Uuid

data class Owner(val userId: Uuid, val privezak: Boolean = false)

context(owner: Owner)
val ownerId: Uuid get() = owner.userId
