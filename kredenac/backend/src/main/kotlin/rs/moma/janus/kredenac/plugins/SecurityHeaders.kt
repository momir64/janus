package rs.moma.janus.kredenac.plugins

import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.application.*

private const val CONTENT_SECURITY_POLICY = "default-src 'self'; script-src 'self'; " +
        "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
        "font-src 'self' https://fonts.gstatic.com; img-src 'self' data: blob:; " +
        "connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; " +
        "frame-ancestors 'none'; frame-src 'none'"

fun Application.configureSecurityHeaders() {
    install(DefaultHeaders) {
        header("X-Frame-Options", "DENY")
        header("Content-Security-Policy", CONTENT_SECURITY_POLICY)
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        header("Permissions-Policy", "camera=(), microphone=(), geolocation=()")
        header("Referrer-Policy", "strict-origin-when-cross-origin")
        header("Cross-Origin-Opener-Policy", "same-origin")
        header("X-Content-Type-Options", "nosniff")
    }
}