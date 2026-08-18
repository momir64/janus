package rs.moma.janus.kredenac.crypto.authentication

import rs.moma.janus.kredenac.repository.TokenRepository
import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import rs.moma.janus.kredenac.email.EmailSender
import kotlin.time.Duration.Companion.minutes
import java.security.SecureRandom
import kotlin.io.encoding.Base64

class MagicLinkService(
    private val tokenRepository: TokenRepository,
    private val emailSender: EmailSender,
    private val frontendOrigin: String
) {
    private val secureRandom = SecureRandom()

    suspend fun request(email: String) {
        // todo email should be different to indicate that user with this email already has an account
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = Base64.UrlSafe.withPadding(ABSENT).encode(bytes)
        tokenRepository.insert(token, 15.minutes, email)

        val link = "$frontendOrigin/verify?token=$token"
        emailSender.send(
            to = email,
            subject = "Sign in to Kredenac",
            html = """
                <p>Click the link below to continue. This link expires in 15 minutes.</p>
                <p><a href="$link">$link</a></p>
                <p>If you didn't request this, you can safely ignore this email.</p>
            """.trimIndent() // todo better-looking email extracted into resources
        )
    }

    suspend fun getEmail(token: String) = tokenRepository.consume(token)
}
