package rs.moma.janus.kredenac

import io.ktor.server.application.Application
import io.ktor.server.engine.embeddedServer
import rs.moma.janus.kredenac.utils.Env
import io.ktor.server.netty.Netty

fun main() {
    embeddedServer(
        port = Env.get("KTOR_PORT").toInt(),
        module = Application::module,
        factory = Netty,
    ).start(wait = true)
}