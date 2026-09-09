package rs.moma.janus.kredenac.plugins

import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import io.ktor.server.application.*

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json { explicitNulls = false })
    }
}