package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.crypto.authentication.RefreshTokenService
import rs.moma.janus.kredenac.repositories.RefreshTokenRepository
import rs.moma.janus.kredenac.repositories.CredentialRepository
import rs.moma.janus.kredenac.common.UnauthorizedException
import rs.moma.janus.kredenac.repositories.UserRepository
import rs.moma.janus.kredenac.crypto.algorithms.HmacUtil
import rs.moma.janus.kredenac.utils.TestInfra
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.days
import rs.moma.janus.kredenac.common.Owner
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertEquals
import kotlin.test.BeforeTest
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.uuid.Uuid

// Rotation is what makes a stolen refresh token detectable: presenting one twice
// must take down the whole chain rather than mint another session.
class RefreshTokenTest {
    private val users = UserRepository(TestInfra.hmacSecret, TestInfra.piiEncryptionKey, TestInfra.masterKey)
    private val credentials = CredentialRepository(TestInfra.hmacSecret, TestInfra.piiEncryptionKey)
    private val repository = RefreshTokenRepository(TestInfra.hmacSecret)
    private val service = RefreshTokenService(repository, TestInfra.hmacSecret)

    private var owner = Owner(Uuid.NIL)
    private var credentialId = Uuid.NIL

    @BeforeTest
    fun setUp() = runBlocking {
        TestInfra.reset()
        owner = Owner(users.insert("alice@example.com"))
        credentialId = credentials.insert(owner.userId, ByteArray(32) { 1 }, "ES256", ByteArray(64), null)
    }

    @Test
    fun `rotating returns a new token for the same session chain`(): Unit = runBlocking {
        val issued = service.issue(owner.userId, credentialId)
        val (userId, next) = service.rotate(issued.refreshToken)

        assertEquals(owner.userId, userId)
        assertEquals(issued.chainId, next.chainId, "rotation started a new chain")
        assertEquals(credentialId, next.credentialId, "rotation lost the passkey it belongs to")
        assertNotEquals(issued.refreshToken, next.refreshToken, "the same token came back")
    }

    @Test
    fun `a repeat inside the grace period is tolerated, for a retried request`(): Unit = runBlocking {
        val issued = service.issue(owner.userId, credentialId)
        service.rotate(issued.refreshToken)
        service.rotate(issued.refreshToken)
    }

    @Test
    fun `presenting a rotated token later revokes the whole chain`(): Unit = runBlocking {
        val strict = RefreshTokenService(repository, TestInfra.hmacSecret, rotationGracePeriod = ZERO)
        val issued = strict.issue(owner.userId, credentialId)
        val (_, second) = strict.rotate(issued.refreshToken)

        assertFailsWith<UnauthorizedException> { strict.rotate(issued.refreshToken) }

        // The reuse takes down every token descended from that sign-in, not just the copy.
        assertNull(repository.findByHash(hash(second.refreshToken)))
        assertFailsWith<UnauthorizedException> { strict.rotate(second.refreshToken) }
    }

    @Test
    fun `a missing, unknown or expired token is refused`(): Unit = runBlocking {
        assertFailsWith<UnauthorizedException> { service.rotate(null) }
        assertFailsWith<UnauthorizedException> { service.rotate("never-issued") }

        repository.insert(owner.userId, credentialId, Uuid.random(), hash("stale"), Clock.System.now() - 1.days)
        assertFailsWith<UnauthorizedException> { service.rotate("stale") }
    }

    @Test
    fun `logging out ends the chain`(): Unit = runBlocking {
        val issued = service.issue(owner.userId, credentialId)
        service.revoke(issued.refreshToken)

        assertNull(repository.findByHash(hash(issued.refreshToken)))
        assertFailsWith<UnauthorizedException> { service.rotate(issued.refreshToken) }
    }

    @Test
    fun `deleting a passkey ends the sessions opened with it`(): Unit = runBlocking {
        val other = credentials.insert(owner.userId, ByteArray(32) { 2 }, "ES256", ByteArray(64), null)
        val doomed = service.issue(owner.userId, credentialId)
        val survivor = service.issue(owner.userId, other)

        context(owner) { repository.deleteChainsForCredential(credentialId) }

        assertFailsWith<UnauthorizedException> { service.rotate(doomed.refreshToken) }
        service.rotate(survivor.refreshToken)
    }

    @Test
    fun `the sweep removes only what has expired`(): Unit = runBlocking {
        val live = service.issue(owner.userId, credentialId)
        repository.insert(owner.userId, credentialId, Uuid.random(), hash("expired"), Clock.System.now() - 1.days)

        repository.deleteExpired()

        assertNull(repository.findByHash(hash("expired")))
        service.rotate(live.refreshToken)
    }

    private fun hash(token: String) = HmacUtil.hash(TestInfra.hmacSecret, token)
}
