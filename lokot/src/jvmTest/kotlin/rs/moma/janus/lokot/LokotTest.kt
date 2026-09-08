package rs.moma.janus.lokot

import kotlin.time.Duration.Companion.seconds
import rs.moma.janus.lokot.files.LokotHeader
import rs.moma.janus.lokot.externals.Crypto
import rs.moma.janus.lokot.files.LokotFile
import rs.moma.janus.lokot.files.VaultBody
import rs.moma.janus.lokot.files.asChars
import kotlin.io.path.createTempFile
import rs.moma.janus.lokot.files.Kek
import kotlin.io.encoding.Base64
import kotlin.io.path.writeBytes
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class LokotTest {
    private val prfOutput = ByteArray(32) { (it + 100).toByte() }
    private val credentialId = ByteArray(48) { (it + 7).toByte() }

    private val values = mapOf(
        "JWT_SECRET" to Base64.encode("secret".toByteArray()),
        "RP_ID" to "example.com",
    )

    private fun vault(): Lokot {
        val kek = ByteArray(Crypto.KEY_SIZE) { it.toByte() }
        val header = LokotHeader(
            project = "example",
            salt = ByteArray(LokotHeader.SALT_SIZE) { it.toByte() },
            credentials = listOf(Kek.wrap(prfOutput, credentialId, "example.com", kek)),
        )
        val path = createTempFile("lokot", ".vault")
        path.writeBytes(LokotFile.build(header, VaultBody("project = \"example\"", values.asChars()), kek))
        return Lokot(path)
    }

    private fun url(bytes: ByteArray) = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)

    private fun body(id: ByteArray = credentialId, output: ByteArray = prfOutput) =
        """{"credentialId":"${url(id)}","output":"${url(output)}"}"""

    @Test
    fun `the posted body opens it`() {
        val lokot = vault()
        assertFalse(lokot.isUnlocked())
        assertTrue(lokot.unlock(body()))
        assertTrue(lokot.isUnlocked())
        assertEquals("example.com", String(lokot.get("RP_ID")))
        assertTrue(lokot.getBytes("JWT_SECRET").contentEquals("secret".toByteArray()))
    }

    @Test
    fun `a wrong output opens nothing`() {
        val lokot = vault()
        assertFalse(lokot.unlock(body(output = ByteArray(32))))
        assertFalse(lokot.unlock(body(id = ByteArray(48))))
        assertFalse(lokot.unlock("""{"credentialId":"###","output":"###"}"""))
        assertFalse(lokot.unlock("not json at all"))
        assertFalse(lokot.isUnlocked())
    }

    @Test
    fun `what it hands out is a copy, so wiping it leaves the vault intact`() {
        val lokot = vault()
        lokot.unlock(body())
        lokot.get("RP_ID").fill('x')
        assertEquals("example.com", String(lokot.get("RP_ID")))
    }

    @Test
    fun `locking forgets the values`() {
        val lokot = vault()
        assertTrue(lokot.unlock(body()))
        lokot.lock()
        assertFalse(lokot.isUnlocked())
        assertTrue(runCatching { lokot.get("RP_ID") }.isFailure)
    }

    @Test
    fun `the challenge carries only this origin's credentials`() {
        val challenge = vault().challenge("example.com")
        assertEquals(listOf(url(credentialId)), challenge.credentialIds.map(::url))
        assertTrue(challenge.json.contains("\"rpId\":\"example.com\""))
        assertTrue(vault().challenge("lokot.localhost").credentialIds.isEmpty())
    }

    @Test
    fun `awaitUnlock waits rather than polls`() {
        val lokot = vault()
        assertFalse(lokot.awaitUnlock(1.seconds))
        Thread { Thread.sleep(100); lokot.unlock(body()) }.start()
        assertTrue(lokot.awaitUnlock(10.seconds))
    }
}
