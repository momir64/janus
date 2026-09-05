package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.crypto.webauthn.verifyRegistration
import rs.moma.janus.kredenac.crypto.webauthn.ParsedAttestation
import rs.moma.janus.kredenac.repositories.CredentialRepository
import rs.moma.janus.kredenac.crypto.webauthn.WebAuthnService
import rs.moma.janus.kredenac.common.UnauthorizedException
import rs.moma.janus.kredenac.crypto.webauthn.LoginOutcome
import rs.moma.janus.kredenac.repositories.TokenRepository
import rs.moma.janus.kredenac.crypto.webauthn.verifyLogin
import rs.moma.janus.kredenac.repositories.UserRepository
import rs.moma.janus.kredenac.common.BadRequestException
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import rs.moma.janus.kredenac.utils.KeyAttestation
import rs.moma.janus.kredenac.utils.Authenticator
import kotlin.time.Duration.Companion.minutes
import rs.moma.janus.kredenac.utils.TestInfra
import rs.moma.janus.kredenac.dtos.Base64Url
import rs.moma.janus.kredenac.common.Owner
import rs.moma.janus.kredenac.utils.Cbor
import kotlin.test.assertContentEquals
import kotlinx.coroutines.runBlocking
import java.security.MessageDigest
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.BeforeTest
import kotlin.test.assertTrue
import kotlin.test.assertIs
import kotlin.test.Test
import kotlin.uuid.Uuid

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class CeremonyTest {
    private val tokens = TokenRepository(TestInfra.redis, TestInfra.tokenEncryptionKey, TestInfra.hmacSecret)
    private val credentials = CredentialRepository(TestInfra.hmacSecret, TestInfra.piiEncryptionKey)
    private val users = UserRepository(TestInfra.hmacSecret, TestInfra.piiEncryptionKey, TestInfra.masterKey)
    private val privezak = KeyAttestation()
    private val webAuthn = WebAuthnService(
        "kredenac.moma.rs", "https://kredenac.moma.rs",
        TestInfra.hmacSecret, tokens, credentials, privezak.root
    )

    private val device = Authenticator()
    private var owner = Owner(Uuid.NIL)

    @BeforeTest
    fun setUp(): Unit = runBlocking {
        TestInfra.reset()
        owner = Owner(users.insert("alice@example.com"))
    }

    private suspend fun register(): Uuid {
        tokens.insert("magic-link", 15.minutes, "alice@example.com")
        val session = webAuthn.start("magic-link")

        val clientData = device.clientData("webauthn.create", session.challenge)
        val (parsed, email) = webAuthn.verifyRegistration(
            Base64Url(device.encode(clientData)),
            Base64Url(device.encode(device.attestationObject())),
            session.cookie
        )

        assertEquals("alice@example.com", email)
        return credentials.insert(owner.userId, parsed.credentialId, parsed.algorithm, parsed.publicKey, parsed.aaguid)
    }

    private suspend fun registerAttested(attestation: KeyAttestation): ParsedAttestation {
        tokens.insert("magic-link", 15.minutes, "alice@example.com")
        val session = webAuthn.start("magic-link")

        val clientData = device.clientData("webauthn.create", session.challenge)
        val challenge = MessageDigest.getInstance("SHA-256").digest(clientData)
        val chain = attestation.chain(device.publicKey, challenge)

        return webAuthn.verifyRegistration(
            Base64Url(device.encode(clientData)),
            Base64Url(device.encode(device.androidKeyAttestationObject(chain, clientData))),
            session.cookie
        ).first
    }

    private suspend fun login(count: Long, origin: String = "https://kredenac.moma.rs"): LoginOutcome {
        val session = webAuthn.start()
        val clientData = device.clientData("webauthn.get", session.challenge, origin)
        val authData = device.authenticatorData(count)
        return webAuthn.verifyLogin(
            Base64Url(device.encode(device.credentialId)),
            Base64Url(device.encode(clientData)),
            Base64Url(device.encode(authData)),
            Base64Url(device.encode(device.sign(authData, clientData))),
            session.cookie,
            "203.0.113.7",
            "Belgrade, RS"
        )
    }

    @Test
    fun `registering stores the key and the model the authenticator reports`(): Unit = runBlocking {
        val id = register()
        val stored = credentials.findByCredentialId(device.credentialId)!!

        assertEquals(id, stored.id)
        assertEquals("ES256", stored.algorithm)
        assertEquals(device.aaguid, stored.aaguid)
        assertContentEquals(device.publicKey, stored.publicKey)
    }

    @Test
    fun `a valid signature signs in and records the use`(): Unit = runBlocking {
        register()
        val outcome = login(count = 1)

        assertIs<LoginOutcome.Success>(outcome)
        assertEquals(owner.userId, outcome.userId)

        val stored = credentials.findByCredentialId(device.credentialId)!!
        assertEquals(1, stored.signCount)
        assertEquals("203.0.113.7", stored.lastUsedIp)
        assertEquals("Belgrade, RS", stored.lastUsedLocation)
        assertTrue(stored.lastUsedAt != null, "the sign-in was not stamped")
    }

    @Test
    fun `a sign count that does not advance is treated as a clone`(): Unit = runBlocking {
        register()
        assertIs<LoginOutcome.Success>(login(count = 9))

        // The same counter again: a second device replaying a copied credential.
        assertIs<LoginOutcome.CloneDetected>(login(count = 9))
        assertIs<LoginOutcome.CloneDetected>(login(count = 4))
    }

    @Test
    fun `a signature from a different key is refused`(): Unit = runBlocking {
        register()

        val impostor = Authenticator(credentialId = device.credentialId)
        val session = webAuthn.start()
        val clientData = impostor.clientData("webauthn.get", session.challenge)
        val authData = impostor.authenticatorData(5)

        assertFailsWith<UnauthorizedException> {
            webAuthn.verifyLogin(
                Base64Url(impostor.encode(impostor.credentialId)),
                Base64Url(impostor.encode(clientData)),
                Base64Url(impostor.encode(authData)),
                Base64Url(impostor.encode(impostor.sign(authData, clientData))),
                session.cookie, null, null
            )
        }
    }

    @Test
    fun `an assertion from another origin is refused`(): Unit = runBlocking {
        register()
        assertFailsWith<BadRequestException> { login(count = 2, origin = "https://evil.example.com") }
    }

    @Test
    fun `an unknown credential is refused`(): Unit = runBlocking {
        val session = webAuthn.start()
        val clientData = device.clientData("webauthn.get", session.challenge)
        val authData = device.authenticatorData(1)

        val error = assertFailsWith<UnauthorizedException> {
            webAuthn.verifyLogin(
                Base64Url(device.encode(ByteArray(32) { 99 })),
                Base64Url(device.encode(clientData)),
                Base64Url(device.encode(authData)),
                Base64Url(device.encode(device.sign(authData, clientData))),
                session.cookie, null, null
            )
        }

        assertEquals("passkey_unknown", error.code)
    }

    @Test
    fun `a replayed challenge is refused even with a valid signature`(): Unit = runBlocking {
        register()
        val session = webAuthn.start()
        val clientData = device.clientData("webauthn.get", session.challenge)
        val authData = device.authenticatorData(3)
        val signature = device.sign(authData, clientData)

        suspend fun attempt() = webAuthn.verifyLogin(
            Base64Url(device.encode(device.credentialId)),
            Base64Url(device.encode(clientData)),
            Base64Url(device.encode(authData)),
            Base64Url(device.encode(signature)),
            session.cookie, null, null
        )

        assertIs<LoginOutcome.Success>(attempt())
        assertFailsWith<BadRequestException> { attempt() }
    }

    @Test
    fun `a registration without user verification is refused`(): Unit = runBlocking {
        tokens.insert("magic-link", 15.minutes, "alice@example.com")
        val session = webAuthn.start("magic-link")
        val unverified = Authenticator()

        val attestation = Cbor.map(
            Cbor.text("fmt") to Cbor.text("none"),
            Cbor.text("authData") to Cbor.bytes(unverified.authenticatorData(1, flags = 0x41))
        )

        assertFailsWith<BadRequestException> {
            webAuthn.verifyRegistration(
                Base64Url(unverified.encode(unverified.clientData("webauthn.create", session.challenge))),
                Base64Url(unverified.encode(attestation)),
                session.cookie
            )
        }
    }

    @Test
    fun `a passkey attested by privezak is stored as one`(): Unit = runBlocking {
        val parsed = registerAttested(privezak)
        assertTrue(parsed.privezak)

        credentials.insert(
            owner.userId, parsed.credentialId, parsed.algorithm,
            parsed.publicKey, parsed.aaguid, true
        )
        assertTrue(credentials.findByCredentialId(device.credentialId)!!.privezak)
    }

    @Test
    fun `a passkey that attests to nothing is not privezak`(): Unit = runBlocking {
        tokens.insert("magic-link", 15.minutes, "alice@example.com")
        val session = webAuthn.start("magic-link")
        val clientData = device.clientData("webauthn.create", session.challenge)

        val (parsed, _) = webAuthn.verifyRegistration(
            Base64Url(device.encode(clientData)),
            Base64Url(device.encode(device.attestationObject())),
            session.cookie
        )
        assertFalse(parsed.privezak)
    }

    @Test
    fun `an attestation naming another app is not privezak`(): Unit = runBlocking {
        assertFalse(registerAttested(KeyAttestation(packageName = "rs.moma.janus.privezak.clone")).privezak)
    }

    @Test
    fun `an attestation carrying another signing key is not privezak`(): Unit = runBlocking {
        assertFalse(registerAttested(KeyAttestation(signer = "aa".repeat(32))).privezak)
    }

    @Test
    fun `an attestation rooted anywhere else is not privezak`(): Unit = runBlocking {
        assertFalse(registerAttested(KeyAttestation()).privezak)
    }
}
