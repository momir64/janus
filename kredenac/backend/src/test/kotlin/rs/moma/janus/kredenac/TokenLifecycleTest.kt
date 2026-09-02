package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.repositories.CredentialRepository
import rs.moma.janus.kredenac.crypto.webauthn.WebAuthnService
import rs.moma.janus.kredenac.repositories.TokenRepository
import rs.moma.janus.kredenac.common.BadRequestException
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import rs.moma.janus.kredenac.utils.TestInfra
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.BeforeTest
import kotlin.test.assertTrue
import kotlin.test.Test

// A challenge may be used once, only with the cookie it was issued with, and a token
// traded for one is spent when the registration completes - taking every other
// challenge traded from that token with it.
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class TokenLifecycleTest {
    private val tokens = TokenRepository(TestInfra.redis, TestInfra.tokenEncryptionKey, TestInfra.hmacSecret)
    private val webAuthn = WebAuthnService(
        "kredenac.moma.rs",
        "https://kredenac.moma.rs",
        TestInfra.hmacSecret,
        tokens,
        CredentialRepository(TestInfra.hmacSecret, TestInfra.piiEncryptionKey)
    )

    @BeforeTest
    fun setUp() = runBlocking { TestInfra.reset() }

    @Test
    fun `a challenge is accepted once and never again`(): Unit = runBlocking {
        val session = webAuthn.start()

        webAuthn.verifyChallengeSession(session.challenge, session.cookie)
        assertFailsWith<BadRequestException> { webAuthn.verifyChallengeSession(session.challenge, session.cookie) }
    }

    @Test
    fun `a challenge without its cookie is refused, and stays unspent`(): Unit = runBlocking {
        val session = webAuthn.start()

        assertFailsWith<BadRequestException> { webAuthn.verifyChallengeSession(session.challenge, null) }
        assertFailsWith<BadRequestException> { webAuthn.verifyChallengeSession(session.challenge, "not-the-cookie") }

        // The wrong cookie must not burn the challenge the rightful holder still has.
        webAuthn.verifyChallengeSession(session.challenge, session.cookie)
    }

    @Test
    fun `one challenge's cookie does not open another challenge`(): Unit = runBlocking {
        val first = webAuthn.start()
        val second = webAuthn.start()

        assertFailsWith<BadRequestException> { webAuthn.verifyChallengeSession(second.challenge, first.cookie) }
    }

    @Test
    fun `a challenge hands back what its token carried`(): Unit = runBlocking {
        tokens.insert("magic-link", 15.minutes, "alice@example.com")
        val session = webAuthn.start("magic-link")

        assertEquals("alice@example.com", webAuthn.consumeChallengeBond(session.challenge, session.cookie))
    }

    @Test
    fun `registering spends the token, so a second challenge from it stops working`(): Unit = runBlocking {
        tokens.insert("magic-link", 15.minutes, "alice@example.com")

        // Opening the link twice in one inbox: two live challenges, one token behind them.
        val first = webAuthn.start("magic-link")
        val second = webAuthn.start("magic-link")

        assertEquals("alice@example.com", webAuthn.consumeChallengeBond(first.challenge, first.cookie))
        assertFailsWith<BadRequestException> { webAuthn.consumeChallengeBond(second.challenge, second.cookie) }
    }

    @Test
    fun `trading a link reads it without spending it`(): Unit = runBlocking {
        tokens.insert("magic-link", 15.minutes, "alice@example.com")

        assertEquals("alice@example.com", tokens.peek("magic-link"))
        assertEquals("alice@example.com", tokens.peek("magic-link"))

        assertEquals("alice@example.com", tokens.consume("magic-link"))
        assertFailsWith<BadRequestException> { tokens.peek("magic-link") }
    }

    @Test
    fun `a reauth token is spent by the action it authorises`(): Unit = runBlocking {
        tokens.insert("reauth", 5.minutes, "a-user-id")

        assertEquals("a-user-id", tokens.consume("reauth"))
        assertFailsWith<BadRequestException> { tokens.consume("reauth") }
    }

    @Test
    fun `an unknown or expired token is a bad request, never a silent pass`(): Unit = runBlocking {
        assertFailsWith<BadRequestException> { tokens.consume("never-issued") }
        assertFailsWith<BadRequestException> { tokens.peek("never-issued") }
        assertFalse(tokens.consumePresence("never-issued"))

        tokens.insert("brief", 1.seconds, "value")
        assertTrue(tokens.consumePresence("brief"))
    }

    @Test
    fun `the raw token never appears in redis, only a keyed ciphertext`(): Unit = runBlocking {
        tokens.insert("magic-link", 15.minutes, "alice@example.com")

        val keys = TestInfra.redis.keys("*").let { flow ->
            val collected = mutableListOf<String>()
            flow.collect { collected.add(it) }
            collected
        }

        assertEquals(1, keys.size)
        assertFalse(keys.single().contains("magic-link"), "the token itself is the key")

        val stored = TestInfra.redis.get(keys.single())!!
        assertFalse(stored.contains("alice@example.com"), "the address is stored in the clear")
    }
}
