package rs.moma.janus.kredenac

import io.ktor.server.engine.applicationEnvironment
import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import io.ktor.server.engine.sslConnector
import rs.moma.janus.kredenac.common.Env
import io.ktor.server.netty.Netty
import java.io.FileInputStream
import java.security.KeyStore
import java.io.File

fun main() {
    val keyStore = KeyStore.getInstance("PKCS12").apply {
        load(
            FileInputStream(Env.get("BACKEND_TLS_KEYSTORE_PATH")),
            Env.get("BACKEND_TLS_KEYSTORE_PASSWORD").toCharArray()
        )
    }

    embeddedServer(
        Netty,
        applicationEnvironment {},
        {
            sslConnector(
                keyStore = keyStore,
                keyAlias = "backend",
                keyStorePassword = { Env.get("BACKEND_TLS_KEYSTORE_PASSWORD").toCharArray() },
                privateKeyPassword = { Env.get("BACKEND_TLS_KEYSTORE_PASSWORD").toCharArray() }
            ) {
                port = Env.get("KTOR_PORT").toInt()
                keyStorePath = File(Env.get("BACKEND_TLS_KEYSTORE_PATH"))
            }
        },
        module = Application::module
    ).start(wait = true)
}