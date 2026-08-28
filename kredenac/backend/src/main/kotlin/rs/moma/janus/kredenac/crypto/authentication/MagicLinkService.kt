package rs.moma.janus.kredenac.crypto.authentication

import rs.moma.janus.kredenac.repositories.TokenRepository
import rs.moma.janus.kredenac.repositories.UserRepository
import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import rs.moma.janus.kredenac.services.EmailService
import kotlin.time.Duration.Companion.minutes
import java.security.SecureRandom
import kotlin.io.encoding.Base64

class MagicLinkService(
    private val tokenRepository: TokenRepository,
    private val userRepository: UserRepository,
    private val emailService: EmailService,
    private val frontendOrigin: String
) {
    private val secureRandom = SecureRandom()

    suspend fun request(email: String) {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val token = Base64.UrlSafe.withPadding(ABSENT).encode(bytes)
        tokenRepository.insert(token, 15.minutes, email)

        val link = "$frontendOrigin/verify?token=$token"
        val hasAccount = userRepository.findIdByEmail(email) != null

        val subject = if (hasAccount) "Add a new passkey to Kredenac" else "Finish signing up for Kredenac"
        val intro = if (hasAccount)
            "Click the link below to add a new passkey to your existing account."
        else
            "Click the link below to finish creating your account."

        emailService.send(
            to = email,
            subject = subject,
            html = """
                <p>$intro <br />This link expires in 15 minutes.</p>
                <p><a href="$link">$link</a></p>
                <p>If you didn't request this, you can safely ignore this email.</p>
            """.trimIndent() // todo better-looking email extracted into resources
        )
    }

    suspend fun getEmail(token: String) = tokenRepository.consume(token)
}
