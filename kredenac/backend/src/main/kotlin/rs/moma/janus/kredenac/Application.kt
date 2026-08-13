package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.plugins.configureSecurityHeaders
import rs.moma.janus.kredenac.plugins.configureAuthentication
import rs.moma.janus.kredenac.plugins.configureSerialization
import rs.moma.janus.kredenac.plugins.configureDependencies
import rs.moma.janus.kredenac.plugins.configureStatusPages
import rs.moma.janus.kredenac.plugins.configureDatabase
import rs.moma.janus.kredenac.plugins.configureRouting
import io.ktor.server.application.*

fun Application.module() {
    configureDatabase()
    configureDependencies()
    configureSerialization()
    configureStatusPages()
    configureSecurityHeaders()
    configureAuthentication()
    configureRouting()
}