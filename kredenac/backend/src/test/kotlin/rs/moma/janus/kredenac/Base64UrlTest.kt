package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.common.BadRequestException
import rs.moma.janus.kredenac.dtos.Base64Url
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.io.encoding.Base64
import kotlin.test.Test

class Base64UrlTest {
    private val raw = ByteArray(32) { it.toByte() }

    @Test
    fun `decodes what an authenticator sends, padded or not`() {
        val unpadded = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(raw)
        val padded = Base64.UrlSafe.encode(raw)

        assertContentEquals(raw, Base64Url(unpadded).decode())
        assertContentEquals(raw, Base64Url(padded).decode())
    }

    @Test
    fun `decodes the url-safe alphabet rather than the standard one`() {
        val bytes = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xFE.toByte())
        assertContentEquals(bytes, Base64Url("-__-").decode())
    }

    @Test
    fun `a malformed value is a bad request, not a server error`() {
        for (bad in listOf("!!", "not base64!", "++//")) {
            assertFailsWith<BadRequestException>("input: $bad") { Base64Url(bad).decode() }
        }
    }
}
