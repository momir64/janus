package rs.moma.janus.kredenac

import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import rs.moma.janus.kredenac.common.Env
import rs.moma.janus.kredenac.common.Tls
import io.ktor.server.netty.Netty
import kotlin.io.path.Path

fun main() {
    val keyStore = Tls.keyStore(
        certificate = Path(Env.get("BACKEND_TLS_CERT_PATH")),
        privateKey = Path(Env.get("BACKEND_TLS_KEY_PATH")),
        alias = "backend",
    )

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
                port = Env.get("KTOR_PORT").toInt()
            }
        },
        module = Application::module
    ).start(wait = true)
}