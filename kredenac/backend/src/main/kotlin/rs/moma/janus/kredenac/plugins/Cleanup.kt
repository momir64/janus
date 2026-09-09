package rs.moma.janus.kredenac.plugins

import rs.moma.janus.kredenac.repositories.RefreshTokenRepository
import kotlin.time.Duration.Companion.hours
import io.ktor.server.application.*
import org.koin.ktor.ext.inject
import kotlinx.coroutines.*

fun Application.configureCleanup() {
    val refreshTokenRepository: RefreshTokenRepository by inject()

    val job = launch(Dispatchers.IO) {
        while (isActive) {
            delay(1.hours)
            try {
                refreshTokenRepository.deleteExpired()
            } catch (e: Exception) {
                log.error("Cleanup sweep failed", e)
            }
        }
    }

    monitor.subscribe(ApplicationStopping) {
        job.cancel()
    }
}
