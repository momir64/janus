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

        val link = "$frontendOrigin/verify/$token"

        if (userRepository.findIdByEmail(email) != null)
            emailService.sendRecoveryMagicLink(email, link)
        else
            emailService.sendRegisterMagicLink(email, link)
    }

    suspend fun getEmail(token: String) = tokenRepository.peek(token)
}
