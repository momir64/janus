package rs.moma.janus.kredenac.crypto.authentication

import rs.moma.janus.kredenac.repositories.RefreshTokenRepository
import rs.moma.janus.kredenac.common.UnauthorizedException
import rs.moma.janus.kredenac.crypto.algorithms.HmacUtil
import kotlin.io.encoding.Base64.PaddingOption.ABSENT
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Duration.Companion.days
import org.slf4j.LoggerFactory.getLogger
import java.security.SecureRandom
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.uuid.Uuid

data class IssuedRefreshToken(val refreshToken: String, val chainId: Uuid, val credentialId: Uuid)

class RefreshTokenService(
    private val repository: RefreshTokenRepository,
    private val hmacSecret: ByteArray
) {
    private val secureRandom = SecureRandom()
    private val rotationGracePeriod = 5.seconds
    private val log = getLogger(RefreshTokenService::class.java)

    suspend fun issue(userId: Uuid, credentialId: Uuid, chainId: Uuid = Uuid.random()): IssuedRefreshToken {
        val bytes = ByteArray(32)
        secureRandom.nextBytes(bytes)
        val rawToken = Base64.UrlSafe.withPadding(ABSENT).encode(bytes)
        val token = HmacUtil.hash(hmacSecret, rawToken)
        repository.insert(userId, credentialId, chainId, token, Clock.System.now() + 30.days)
        return IssuedRefreshToken(rawToken, chainId, credentialId)
    }

    suspend fun revoke(refreshToken: String) {
        val stored = repository.findByHash(HmacUtil.hash(hmacSecret, refreshToken)) ?: return
        repository.deleteChain(stored.chainId)
    }

    suspend fun rotate(refreshToken: String?): Pair<Uuid, IssuedRefreshToken> {
        val refreshToken = refreshToken ?: throw UnauthorizedException("Missing refresh token")

        val stored = repository.findByHash(HmacUtil.hash(hmacSecret, refreshToken))
            ?: throw UnauthorizedException("Invalid refresh token")

        if (stored.rotatedAt != null && stored.rotatedAt + rotationGracePeriod <= Clock.System.now()) {
            log.warn("Refresh token reuse detected: chainId=${stored.chainId} userId=${stored.userId}")
            repository.deleteChain(stored.chainId)
            throw UnauthorizedException("Refresh token reuse detected")
        }

        if (stored.expiresAt < Clock.System.now())
            throw UnauthorizedException("Refresh token expired")

        if (stored.rotatedAt == null)
            repository.markRotated(stored)

        return stored.userId to issue(stored.userId, stored.credentialId, stored.chainId)
    }
}
