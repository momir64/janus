package rs.moma.janus.kredenac.plugins

import rs.moma.janus.kredenac.crypto.webauthn.AttestationTrust
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.days
import io.ktor.server.application.*
import org.koin.ktor.ext.inject
import kotlinx.coroutines.*

private val RETRY_INTERVAL = 15.minutes
private val REFRESH_INTERVAL = 1.days

fun Application.configureAttestation() {
    val attestationTrust: AttestationTrust by inject()

    val job = launch(Dispatchers.IO) {
        while (isActive) {
            delay(
                try {
                    attestationTrust.refresh()
                    REFRESH_INTERVAL
                } catch (e: Exception) {
                    log.error("Could not read the Android attestation lists, retrying in $RETRY_INTERVAL", e)
                    RETRY_INTERVAL
                }
            )
        }
    }

    monitor.subscribe(ApplicationStopping) {
        job.cancel()
    }
}
