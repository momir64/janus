package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.repositories.RefreshTokenRepository
import rs.moma.janus.kredenac.repositories.CredentialRepository
import rs.moma.janus.kredenac.repositories.FilesRepository
import rs.moma.janus.kredenac.repositories.NotesRepository
import rs.moma.janus.kredenac.repositories.UserRepository
import rs.moma.janus.kredenac.utils.TestInfra
import kotlin.time.Duration.Companion.days
import rs.moma.janus.kredenac.common.Owner
import kotlin.test.assertContentEquals
import kotlinx.coroutines.runBlocking
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.BeforeTest
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.test.Test
import kotlin.uuid.Uuid

// Every repository method takes an Owner, but the context only makes the value
// available - nothing checks that the query used it. These are the tests that do.
class OwnershipTest {
    private val users = UserRepository(TestInfra.hmacSecret, TestInfra.emailEncryptionKey, TestInfra.masterKey)
    private val credentials = CredentialRepository(TestInfra.hmacSecret)
    private val refreshTokens = RefreshTokenRepository(TestInfra.hmacSecret)
    private val notes = NotesRepository()
    private val files = FilesRepository()

    private var alice = Owner(Uuid.NIL)
    private var mallory = Owner(Uuid.NIL)

    @BeforeTest
    fun setUp() = runBlocking {
        TestInfra.reset()
        alice = Owner(users.insert("alice@example.com"))
        mallory = Owner(users.insert("mallory@example.com"))
    }

    private fun bytes(seed: Int) = ByteArray(16) { (it + seed).toByte() }

    @Test
    fun `a note belongs to one account only`(): Unit = runBlocking {
        val noteId = context(alice) { notes.insert(bytes(1), bytes(2), bytes(3), bytes(4)) }

        context(mallory) {
            assertEquals(emptyList(), notes.findAll())
            assertFalse(notes.update(noteId, bytes(9), bytes(9), bytes(9), bytes(9)), "update reached another account")
            assertFalse(notes.delete(noteId), "delete reached another account")
        }

        context(alice) {
            assertEquals(1, notes.findAll().size, "the owner lost their own note")
            assertContentEquals(bytes(1), notes.findAll().single().encryptedTitle, "another account overwrote it")
        }
    }

    @Test
    fun `a file belongs to one account only`(): Unit = runBlocking {
        val fileId = Uuid.random()
        context(alice) { files.insert(fileId, bytes(1), bytes(2), bytes(3), 10) }

        context(mallory) {
            assertEquals(emptyList(), files.findAll())
            assertNull(files.findById(fileId), "another account could read the row")
            assertFalse(files.delete(fileId), "another account could delete it")
        }

        context(alice) { assertEquals(1, files.findAll().size, "the owner lost their own file") }
    }

    @Test
    fun `a passkey belongs to one account only`(): Unit = runBlocking {
        val credentialId = credentials.insert(alice.userId, bytes(1), "ES256", bytes(2), null)

        context(mallory) {
            assertEquals(emptyList(), credentials.findAll())
            assertFalse(credentials.delete(credentialId), "another account could delete a passkey")
        }

        context(alice) { assertEquals(1, credentials.findAll().size, "the owner lost their own passkey") }
    }

    @Test
    fun `a refresh chain belongs to one account only`(): Unit = runBlocking {
        val credentialId = credentials.insert(alice.userId, bytes(1), "ES256", bytes(2), null)
        val chainId = Uuid.random()
        refreshTokens.insert(alice.userId, credentialId, chainId, "hash-a", Clock.System.now() + 30.days)

        context(mallory) {
            refreshTokens.deleteChainsForCredential(credentialId)
            refreshTokens.deleteAllForUser()
        }

        assertEquals("hash-a", refreshTokens.findByHash("hash-a")?.tokenHash, "another account revoked the session")
    }

    @Test
    fun `wiping one account leaves the other untouched`(): Unit = runBlocking {
        context(alice) { notes.insert(bytes(1), bytes(2), bytes(3), bytes(4)) }
        context(mallory) { notes.insert(bytes(5), bytes(6), bytes(7), bytes(8)) }
        context(alice) { files.insert(Uuid.random(), bytes(1), bytes(2), bytes(3), 1) }
        context(mallory) { files.insert(Uuid.random(), bytes(5), bytes(6), bytes(7), 1) }

        context(alice) {
            notes.deleteAll()
            files.deleteAll()
        }

        context(mallory) {
            assertEquals(1, notes.findAll().size, "deleteAll crossed accounts")
            assertEquals(1, files.findAll().size, "deleteAll crossed accounts")
        }
    }
}
