package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.plugins.configureUnlock
import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import rs.moma.janus.kredenac.common.vault
import io.ktor.server.engine.sslConnector
import rs.moma.janus.kredenac.common.Env
import rs.moma.janus.kredenac.common.Tls
import io.ktor.server.netty.Netty
import java.security.KeyStore
import kotlin.io.path.Path

private const val TIMEOUT_MILLIS = 3000L
private const val GRACE_MILLIS = 500L

fun main() {
    val keyStore = Tls.keyStore(
        certificate = Path(Env.get("BACKEND_TLS_CERT_PATH")),
        privateKey = Path(Env.get("BACKEND_TLS_KEY_PATH")),
        alias = "backend",
    )
    val port = Env.get("KTOR_PORT").toInt()

    val gate = serve(keyStore, port) { configureUnlock(vault) }.start(wait = false)
    vault.awaitUnlock()
    gate.stop(GRACE_MILLIS, TIMEOUT_MILLIS)

    serve(keyStore, port, Application::module).start(wait = true)
}

private fun serve(keyStore: KeyStore, port: Int, module: Application.() -> Unit) =
    embeddedServer(
        Netty,
        applicationEnvironment {},
        {
            sslConnector(
                keyStore = keyStore,
                keyAlias = "backend",
                keyStorePassword = { Tls.password },
                privateKeyPassword = { Tls.password }
            ) {
                this.port = port
            }
        },
        module = module,
    )
