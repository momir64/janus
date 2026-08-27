package rs.moma.janus.kredenac.plugins

import rs.moma.janus.kredenac.crypto.authentication.RefreshTokenService
import rs.moma.janus.kredenac.crypto.authentication.MagicLinkService
import kotlin.io.encoding.Base64.PaddingOption.PRESENT_OPTIONAL
import rs.moma.janus.kredenac.crypto.authentication.CsrfService
import rs.moma.janus.kredenac.repository.RefreshTokenRepository
import rs.moma.janus.kredenac.crypto.authentication.JwtService
import rs.moma.janus.kredenac.repository.FileContentRepository
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import rs.moma.janus.kredenac.crypto.webauthn.WebAuthnService
import rs.moma.janus.kredenac.repository.CredentialRepository
import rs.moma.janus.kredenac.repository.FilesRepository
import rs.moma.janus.kredenac.repository.NotesRepository
import rs.moma.janus.kredenac.repository.TokenRepository
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import rs.moma.janus.kredenac.repository.UserRepository
import rs.moma.janus.kredenac.service.FilesService
import rs.moma.janus.kredenac.service.NotesService
import rs.moma.janus.kredenac.service.UserService
import rs.moma.janus.kredenac.email.EmailSender
import org.koin.core.module.dsl.singleOf
import rs.moma.janus.kredenac.common.Env
import io.lettuce.core.api.coroutines
import io.lettuce.core.ClientOptions
import org.koin.core.qualifier.named
import io.ktor.server.application.*
import io.lettuce.core.RedisClient
import io.lettuce.core.SslOptions
import kotlin.io.encoding.Base64
import org.koin.ktor.plugin.Koin
import io.lettuce.core.RedisURI
import org.koin.dsl.module
import java.io.File

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.configureDependencies() {
    install(Koin) {
        modules(module {
            singleOf(::NotesRepository)
            singleOf(::FilesRepository)

            val hmacSecret = Env.getBytes("DB_HMAC_SECRET")
            val masterKey = Base64.withPadding(PRESENT_OPTIONAL).decode(Env.get("MASTER_KEY_BASE64"))
            val emailEncryptionKey = Base64.withPadding(PRESENT_OPTIONAL).decode(Env.get("EMAIL_ENCRYPTION_KEY_BASE64"))

            single { RefreshTokenRepository(hmacSecret) }

            single(named("rpOrigin")) { Env.get("RP_ORIGIN") }
            single(named("rpId")) { Env.get("RP_ID") }

            val truststore = File(Env.get("REDIS_TLS_TRUSTSTORE_PATH"))
            val sslOptions = SslOptions.builder().jdkSslProvider()
                .truststore(truststore, Env.get("REDIS_TLS_TRUSTSTORE_PASSWORD")).build()

            val redisUri = RedisURI.Builder.redis(Env.get("REDIS_HOST"), Env.get("REDIS_PORT").toInt())
                .withSsl(true).withVerifyPeer(true).withPassword(Env.get("REDIS_PASSWORD").toCharArray()).build()
            val redisClient = RedisClient.create(redisUri)
            redisClient.options = ClientOptions.builder().sslOptions(sslOptions).build()
            single<RedisCoroutinesCommands<String, String>> { redisClient.connect().coroutines() }
            single { TokenRepository(get(), emailEncryptionKey, hmacSecret) }

            single { JwtService(Env.getBytes("JWT_SECRET")) }
            single { CsrfService(Env.getBytes("CSRF_SECRET")) }
            single { RefreshTokenService(get(), hmacSecret) }

            single { UserRepository(hmacSecret, emailEncryptionKey, masterKey) }
            single { CredentialRepository(hmacSecret) }

            single { WebAuthnService(get(named("rpId")), get(named("rpOrigin")), hmacSecret, get(), get()) }

            single { EmailSender(Env.get("RESEND_API_KEY"), Env.get("RESEND_FROM_EMAIL")) }
            single { MagicLinkService(get(), get(), get(), get(named("rpOrigin"))) }

            single {
                FileContentRepository(
                    Env.get("MINIO_HOST"), Env.get("MINIO_PORT"), Env.get("MINIO_ROOT_USER"),
                    Env.get("MINIO_ROOT_PASSWORD"), Env.get("MINIO_BUCKET")
                )
            }

            single { UserService(get(), get(), get(), get(), get(), get(), get()) }
            single { FilesService(get(), get(), get()) }
            singleOf(::NotesService)
        })
    }
}