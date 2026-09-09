package rs.moma.janus.kredenac.plugins

import rs.moma.janus.kredenac.crypto.authentication.RefreshTokenService
import rs.moma.janus.kredenac.crypto.authentication.MagicLinkService
import rs.moma.janus.kredenac.repositories.RefreshTokenRepository
import rs.moma.janus.kredenac.repositories.FileContentRepository
import rs.moma.janus.kredenac.crypto.authentication.CsrfService
import rs.moma.janus.kredenac.repositories.CredentialRepository
import rs.moma.janus.kredenac.crypto.authentication.JwtService
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import rs.moma.janus.kredenac.crypto.webauthn.WebAuthnService
import rs.moma.janus.kredenac.repositories.FilesRepository
import rs.moma.janus.kredenac.repositories.NotesRepository
import rs.moma.janus.kredenac.repositories.TokenRepository
import rs.moma.janus.kredenac.repositories.UserRepository
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import rs.moma.janus.kredenac.services.EmailService
import rs.moma.janus.kredenac.services.FilesService
import rs.moma.janus.kredenac.services.NotesService
import rs.moma.janus.kredenac.services.UserService
import rs.moma.janus.kredenac.common.vault
import rs.moma.janus.kredenac.common.text
import rs.moma.janus.lokot.externals.wipe
import javax.net.ssl.TrustManagerFactory
import org.koin.core.module.dsl.singleOf
import rs.moma.janus.kredenac.common.Env
import io.lettuce.core.api.coroutines
import io.lettuce.core.ClientOptions
import org.koin.core.qualifier.named
import io.ktor.server.application.*
import io.lettuce.core.RedisClient
import io.lettuce.core.SslOptions
import org.koin.ktor.plugin.Koin
import io.lettuce.core.RedisURI
import org.koin.dsl.module

@OptIn(ExperimentalLettuceCoroutinesApi::class)
fun Application.configureDependencies(redisTrustManager: TrustManagerFactory) {
    install(Koin) {
        modules(module {
            singleOf(::NotesRepository)
            singleOf(::FilesRepository)

            val hmacSecret = vault.getBytes("DB_HMAC_SECRET")
            val masterKey = vault.getBytes("MASTER_KEY_BASE64")
            val piiEncryptionKey = vault.getBytes("PII_ENCRYPTION_KEY_BASE64")
            val tokenEncryptionKey = vault.getBytes("TOKEN_ENCRYPTION_KEY_BASE64")
            val rpOrigin = vault.text("RP_ORIGIN")
            val minioUser = vault.text("MINIO_ROOT_USER")
            val minioPassword = vault.text("MINIO_ROOT_PASSWORD")
            val minioBucket = vault.text("MINIO_BUCKET")

            single { RefreshTokenRepository(hmacSecret) }

            single(named("rpOrigin")) { rpOrigin }
            single(named("rpId")) { Env.get("RP_ID") }

            val sslOptions = SslOptions.builder().jdkSslProvider().trustManager(redisTrustManager).build()

            val redisUri = RedisURI.Builder.redis(Env.get("REDIS_HOST", "localhost"), Env.get("REDIS_PORT").toInt())
                .withSsl(true).withVerifyPeer(true).withPassword(vault.get("REDIS_PASSWORD")).build()
            val redisClient = RedisClient.create(redisUri)
            redisClient.options = ClientOptions.builder().sslOptions(sslOptions).build()
            single<RedisCoroutinesCommands<String, String>> { redisClient.connect().coroutines() }
            single { TokenRepository(get(), tokenEncryptionKey, hmacSecret) }

            val csrfSecret = vault.getBytes("CSRF_SECRET")
            val jwtSecret = vault.getBytes("JWT_SECRET")
            val csrfService = CsrfService(csrfSecret)
            val jwtService = JwtService(jwtSecret)
            csrfSecret.wipe()
            jwtSecret.wipe()
            single { csrfService }
            single { jwtService }
            single { RefreshTokenService(get(), hmacSecret) }

            single { UserRepository(hmacSecret, piiEncryptionKey, masterKey) }
            single { CredentialRepository(hmacSecret, piiEncryptionKey) }

            single { WebAuthnService(get(named("rpId")), get(named("rpOrigin")), hmacSecret, get(), get()) }

            val emailService = EmailService(vault.text("RESEND_API_KEY"), vault.text("RESEND_FROM_EMAIL"), rpOrigin)
            single { emailService }
            single { MagicLinkService(get(), get(), get(), get(named("rpOrigin"))) }

            single {
                FileContentRepository(
                    Env.get("MINIO_HOST"), Env.get("MINIO_PORT"), minioUser, minioPassword, minioBucket
                )
            }

            single { UserService(get(), get(), get(), get(), get(), get(), get(), get()) }
            single { FilesService(get(), get(), get()) }
            singleOf(::NotesService)
        })
    }
}