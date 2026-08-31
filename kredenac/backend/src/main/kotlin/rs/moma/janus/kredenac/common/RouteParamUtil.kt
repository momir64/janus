package rs.moma.janus.kredenac.common

import io.ktor.server.application.ApplicationCall
import io.ktor.server.util.getOrFail
import kotlin.uuid.Uuid

internal fun ApplicationCall.uuidParam(name: String): Uuid {
    val raw = parameters.getOrFail(name)
    return try {
        Uuid.parse(raw)
    } catch (_: IllegalArgumentException) {
        throw BadRequestException("Invalid $name")
    }
}
