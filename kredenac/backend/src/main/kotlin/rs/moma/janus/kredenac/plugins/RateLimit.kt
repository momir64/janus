package rs.moma.janus.kredenac.plugins

import kotlin.time.Duration.Companion.minutes
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.origin
import io.ktor.server.application.*

val authChallengeRateLimit = RateLimitName("auth-ceremony")
val authRefreshRateLimit = RateLimitName("auth-refresh")
val magicLinkRateLimit = RateLimitName("magic-link")

private fun clientKey(call: ApplicationCall) =
    call.request.headers["CF-Connecting-IP"] ?: call.request.origin.remoteHost

fun Application.configureRateLimit() {
    install(RateLimit) {
        register(authChallengeRateLimit) {
            rateLimiter(limit = 15, refillPeriod = 1.minutes)
            requestKey(::clientKey)
        }
        register(authRefreshRateLimit) {
            rateLimiter(limit = 30, refillPeriod = 1.minutes)
            requestKey(::clientKey)
        }
        register(magicLinkRateLimit) {
            rateLimiter(limit = 3, refillPeriod = 1.minutes)
            requestKey(::clientKey)
        }
    }
}