package rs.moma.janus.kredenac.plugins

import kotlin.io.encoding.Base64.PaddingOption.PRESENT_OPTIONAL
import rs.moma.janus.kredenac.repository.RefreshTokenRepository
import rs.moma.janus.kredenac.repository.CredentialRepository
import rs.moma.janus.kredenac.repository.NotesRepository
import rs.moma.janus.kredenac.repository.UserRepository
import org.koin.core.module.dsl.singleOf
import rs.moma.janus.kredenac.utils.Env
import rs.moma.janus.kredenac.service.*
import org.koin.core.qualifier.named
import io.ktor.server.application.*
import kotlin.io.encoding.Base64
import org.koin.ktor.plugin.Koin
import org.koin.dsl.module

fun Application.configureDependencies() {
    install(Koin) {
        modules(module {
            singleOf(::UserRepository)
            singleOf(::CredentialRepository)
            singleOf(::RefreshTokenRepository)
            singleOf(::NotesRepository)

            single { JwtService(Env.get("JWT_SECRET")) }
            single { CsrfService(Env.get("CSRF_SECRET")) }
            singleOf(::RefreshTokenService)

            val masterKey = Base64.withPadding(PRESENT_OPTIONAL).decode(Env.get("MASTER_KEY_BASE64"))
            single(named("rpOrigin")) { Env.get("RP_ORIGIN") }
            single(named("rpId")) { Env.get("RP_ID") }

            single { RegistrationService(get(named("rpId")), get(named("rpOrigin"))) }
            single { AssertionService(get(), get(), get(named("rpId")), get(named("rpOrigin"))) }
            single { UserService(get(), get(), masterKey) }

            singleOf(::NotesService)
        })
    }
}