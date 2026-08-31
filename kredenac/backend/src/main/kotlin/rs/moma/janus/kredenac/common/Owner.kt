package rs.moma.janus.kredenac.common

import kotlin.uuid.Uuid

@JvmInline
value class Owner(val userId: Uuid)

context(owner: Owner)
val ownerId: Uuid get() = owner.userId
