package rs.moma.janus.kredenac.plugins

import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.application.*

fun Application.configureSecurityHeaders() {
    install(DefaultHeaders) {
        header("X-Frame-Options", "DENY")
        header("Content-Security-Policy", "frame-ancestors 'none'; frame-src 'none'")
    }
}