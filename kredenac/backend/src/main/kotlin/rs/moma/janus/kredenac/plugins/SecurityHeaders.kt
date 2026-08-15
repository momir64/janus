package rs.moma.janus.kredenac.plugins

import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.application.*

fun Application.configureSecurityHeaders() {
    install(DefaultHeaders) {
        header("X-Frame-Options", "DENY")
        header("Content-Security-Policy", "frame-ancestors 'none'; frame-src 'none'")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("Cross-Origin-Opener-Policy", "same-origin")
        header("X-Content-Type-Options", "nosniff")
    }
}