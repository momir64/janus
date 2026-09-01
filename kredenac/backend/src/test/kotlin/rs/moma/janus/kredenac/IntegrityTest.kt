package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.repositories.CredentialRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import rs.moma.janus.kredenac.common.CompromisedException
import rs.moma.janus.kredenac.repositories.UserRepository
import rs.moma.janus.kredenac.tables.CredentialTable
import rs.moma.janus.kredenac.utils.TestInfra
import org.jetbrains.exposed.v1.jdbc.update
import rs.moma.janus.kredenac.common.Owner
import org.jetbrains.exposed.v1.core.eq
import kotlinx.coroutines.runBlocking
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.uuid.Uuid

// The integrity hash is only worth having if a changed row is refused on read.
// Each case edits one column behind the repository's back, the way anyone with
// database access could, and expects the read to fail rather than return it.
class IntegrityTest {
    private val users = UserRepository(TestInfra.hmacSecret, TestInfra.emailEncryptionKey, TestInfra.masterKey)
    private val credentials = CredentialRepository(TestInfra.hmacSecret)

    private var owner = Owner(Uuid.NIL)
    private var credentialId = Uuid.NIL

    private val credentialBytes = ByteArray(32) { it.toByte() }
    private val publicKey = ByteArray(64) { (it + 1).toByte() }
    private val aaguid = Uuid.parse("fcb1bcb4-f370-078c-6993-bc24d0ae3fbe")

    @BeforeTest
    fun setUp() = runBlocking {
        TestInfra.reset()
        owner = Owner(users.insert("alice@example.com"))
        credentialId = credentials.insert(owner.userId, credentialBytes, "ES256", publicKey, aaguid)
    }

    private fun tamper(edit: CredentialTable.(org.jetbrains.exposed.v1.core.statements.UpdateStatement) -> Unit) =
        transaction { CredentialTable.update({ CredentialTable.id eq credentialId }) { edit(CredentialTable, it) } }

    private fun assertRefused(): Unit = runBlocking {
        assertFailsWith<CompromisedException> { credentials.findByCredentialId(credentialBytes) }
        assertFailsWith<CompromisedException> { context(owner) { credentials.findAll() } }
    }

    @Test
    fun `an untouched row reads back exactly as it was written`(): Unit = runBlocking {
        val stored = credentials.findByCredentialId(credentialBytes)!!
        assertEquals(owner.userId, stored.userId)
        assertEquals("ES256", stored.algorithm)
        assertEquals(aaguid, stored.aaguid)
        assertEquals(0, stored.signCount)
    }

    @Test
    fun `a swapped public key is refused`() {
        tamper { statement -> statement[publicKey] = ByteArray(64) { 0 } }
        assertRefused()
    }

    @Test
    fun `a rewound sign count is refused, so clone detection cannot be disarmed`() {
        runBlocking { credentials.recordUse(credentials.findByCredentialId(credentialBytes)!!, 40, null, null) }
        tamper { statement -> statement[signCount] = 0 }
        assertRefused()
    }

    @Test
    fun `a credential moved to another account is refused`(): Unit = runBlocking {
        val mallory = users.insert("mallory@example.com")
        tamper { statement -> statement[userId] = mallory }

        // The row no longer answers to its old owner at all, and the account it was
        // moved to cannot read it either, because userId is inside the hash.
        assertFailsWith<CompromisedException> { credentials.findByCredentialId(credentialBytes) }
        assertFailsWith<CompromisedException> { context(Owner(mallory)) { credentials.findAll() } }
        assertEquals(emptyList(), context(owner) { credentials.findAll() })
    }

    @Test
    fun `an edited credential id is refused`() {
        tamper { statement -> statement[CredentialTable.credentialId] = ByteArray(32) { 9 } }
        runBlocking { assertFailsWith<CompromisedException> { credentials.findByCredentialId(ByteArray(32) { 9 }) } }
    }

    @Test
    fun `a relabelled device is refused, since the name is shown next to a delete button`() {
        tamper { statement -> statement[CredentialTable.aaguid] = Uuid.random() }
        assertRefused()
    }

    @Test
    fun `a downgraded algorithm is refused`() {
        tamper { statement -> statement[algorithm] = "RS256" }
        assertRefused()
    }

    @Test
    fun `recording a use rewrites the hash, so the row still reads`(): Unit = runBlocking {
        val stored = credentials.findByCredentialId(credentialBytes)!!
        credentials.recordUse(stored, 7, "203.0.113.4", "Belgrade, RS")

        val reread = credentials.findByCredentialId(credentialBytes)!!
        assertEquals(7, reread.signCount)
        assertEquals("203.0.113.4", reread.lastUsedIp)
        assertEquals("Belgrade, RS", reread.lastUsedLocation)
    }
}
