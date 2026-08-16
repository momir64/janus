package rs.moma.janus.kredenac.plugins

import rs.moma.janus.kredenac.crypto.authentication.RefreshTokenService
import kotlin.io.encoding.Base64.PaddingOption.PRESENT_OPTIONAL
import rs.moma.janus.kredenac.crypto.authentication.CsrfService
import rs.moma.janus.kredenac.repository.RefreshTokenRepository
import rs.moma.janus.kredenac.crypto.authentication.JwtService
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import rs.moma.janus.kredenac.crypto.webauthn.WebAuthnService
import rs.moma.janus.kredenac.repository.CredentialRepository
import rs.moma.janus.kredenac.repository.ChallengeRepository
import rs.moma.janus.kredenac.repository.NotesRepository
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import rs.moma.janus.kredenac.repository.UserRepository
import rs.moma.janus.kredenac.service.NotesService
import rs.moma.janus.kredenac.service.UserService
import org.koin.core.module.dsl.singleOf
import rs.moma.janus.kredenac.common.Env
import io.lettuce.core.api.coroutines
import org.koin.core.qualifier.named
import io.ktor.server.application.*
import io.lettuce.core.RedisClient
import kotlin.io.encoding.Base64
import org.koin.ktor.plugin.Koin
import io.lettuce.core.RedisURI
import org.koin.dsl.module

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.configureDependencies() {
    install(Koin) {
        modules(module {
            singleOf(::NotesRepository)

            val hmacSecret = Env.getBytes("DB_HMAC_SECRET")
            val masterKey = Base64.withPadding(PRESENT_OPTIONAL).decode(Env.get("MASTER_KEY_BASE64"))
            val emailEncryptionKey = Base64.withPadding(PRESENT_OPTIONAL).decode(Env.get("EMAIL_ENCRYPTION_KEY_BASE64"))

            single { RefreshTokenRepository(hmacSecret) }

            single(named("rpOrigin")) { Env.get("RP_ORIGIN") }
            single(named("rpId")) { Env.get("RP_ID") }

            val redisUri = RedisURI.Builder.redis(Env.get("REDIS_HOST"), Env.get("REDIS_PORT").toInt())
                .withPassword(Env.get("REDIS_PASSWORD").toCharArray()).build()
            val redisClient = RedisClient.create(redisUri)
            single<RedisCoroutinesCommands<String, String>> { redisClient.connect().coroutines() }
            single { ChallengeRepository(get()) }

            single { JwtService(Env.getBytes("JWT_SECRET")) }
            single { CsrfService(Env.getBytes("CSRF_SECRET")) }
            single { RefreshTokenService(get(), hmacSecret) }

            single { UserRepository(hmacSecret, emailEncryptionKey, masterKey) }
            single { CredentialRepository(hmacSecret) }

            single { WebAuthnService(get(named("rpId")), get(named("rpOrigin")), hmacSecret, get(), get()) }

            single { UserService(get(), get(), get()) }
            singleOf(::NotesService)
        })
    }
}
