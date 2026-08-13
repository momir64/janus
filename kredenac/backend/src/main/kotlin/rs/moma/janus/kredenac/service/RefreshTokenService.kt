package rs.moma.janus.kredenac.service

import rs.moma.janus.kredenac.repository.RefreshTokenRepository
import rs.moma.janus.kredenac.utils.UnauthorizedException
import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.days
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.uuid.Uuid

data class IssuedRefreshToken(val refreshToken: String, val chainId: Uuid)

class RefreshTokenService(private val repository: RefreshTokenRepository) {
    private val secureRandom = SecureRandom()
    private val rotationGracePeriod = 5.seconds

    suspend fun issue(userId: Uuid, chainId: Uuid = Uuid.random()): IssuedRefreshToken {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val rawToken = Base64.UrlSafe.withPadding(ABSENT).encode(bytes)
        repository.insert(userId, chainId, hash(rawToken), Clock.System.now() + 30.days)
        return IssuedRefreshToken(rawToken, chainId)
    }

    suspend fun rotate(refreshToken: String): Pair<Uuid, IssuedRefreshToken> {
        val stored = repository.findByHash(hash(refreshToken))
            ?: throw UnauthorizedException("Invalid refresh token")

        if (stored.revokedAt != null)
            throw UnauthorizedException("Refresh token revoked")

        if (stored.rotatedAt != null && stored.rotatedAt + rotationGracePeriod <= Clock.System.now()) {
            repository.revokeChain(stored.chainId)
            throw UnauthorizedException("Refresh token reuse detected")
        }

        if (stored.expiresAt < Clock.System.now())
            throw UnauthorizedException("Refresh token expired")

        if (stored.rotatedAt == null)
            repository.markRotated(stored.id)

        return stored.userId to issue(stored.userId, stored.chainId)
    }

    private fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
