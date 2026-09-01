package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.crypto.webauthn.CborValue.Companion.get
import rs.moma.janus.kredenac.crypto.webauthn.CborParseException
import rs.moma.janus.kredenac.crypto.webauthn.CborValue
import rs.moma.janus.kredenac.utils.Cbor
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.Test

class CborUtilTest {
    private fun parse(bytes: ByteArray) = CborValue.from(bytes)

    @Test
    fun `reads the value types an attestation object is made of`() {
        val map = parse(
            Cbor.map(
                Cbor.text("fmt") to Cbor.text("none"),
                Cbor.text("authData") to Cbor.bytes(byteArrayOf(1, 2, 3)),
                Cbor.text("count") to Cbor.uint(7),
                Cbor.text("flag") to Cbor.bool(true),
                Cbor.text("nothing") to Cbor.nul
            )
        )!!

        assertEquals("none", (map["fmt"] as CborValue.TextStr).value)
        assertContentEquals(byteArrayOf(1, 2, 3), map["authData"]?.asByteStr())
        assertEquals(7, map["count"]?.asInteger())
        assertEquals(CborValue.Bool(true), map["flag"])
        assertEquals(CborValue.Null, map["nothing"])
    }

    @Test
    fun `reads negative integers, which COSE uses for both keys and algorithms`() {
        val map = parse(
            Cbor.map(
                Cbor.uint(3) to Cbor.nint(-7),
                Cbor.nint(-1) to Cbor.uint(1),
                Cbor.nint(-2) to Cbor.bytes(ByteArray(32) { 9 })
            )
        )!!

        assertEquals(-7, map[3L]?.asInteger())
        assertEquals(1, map[-1L]?.asInteger())
        assertEquals(32, map[-2L]?.asByteStr()?.size)
    }

    @Test
    fun `reads arguments at every width`() {
        for (value in listOf(0L, 23L, 24L, 255L, 256L, 65535L, 65536L, 4294967295L, 4294967296L)) {
            val map = parse(Cbor.map(Cbor.text("n") to Cbor.uint(value)))!!
            assertEquals(value, map["n"]?.asInteger(), "argument width for $value")
        }
    }

    @Test
    fun `reads nested maps and arrays`() {
        val map = parse(
            Cbor.map(
                Cbor.text("inner") to Cbor.map(Cbor.text("deep") to Cbor.uint(1)),
                Cbor.text("list") to Cbor.array(Cbor.uint(1), Cbor.uint(2))
            )
        )!!

        val inner = map["inner"] as CborValue.Map
        assertEquals(1, inner["deep"]?.asInteger())
        assertEquals(2, (map["list"] as CborValue.Arr).value.size)
    }

    @Test
    fun `starts at an offset, the way a COSE key follows the credential id`() {
        val prefix = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte())
        val map = CborValue.from(prefix + Cbor.map(Cbor.text("k") to Cbor.uint(5)), prefix.size)!!

        assertEquals(5, map["k"]?.asInteger())
    }

    @Test
    fun `answers null when the top level is not a map`() {
        assertNull(parse(Cbor.uint(1)))
        assertNull(parse(Cbor.array(Cbor.uint(1))))
        assertNull(parse(Cbor.bytes(byteArrayOf(1))))
    }

    @Test
    fun `rejects truncated input rather than reading past the end`() {
        val whole = Cbor.map(Cbor.text("authData") to Cbor.bytes(ByteArray(20)))
        for (cut in 1..4) {
            assertFailsWith<CborParseException>("truncated by $cut") {
                parse(whole.copyOfRange(0, whole.size - cut))
            }
        }
        assertFailsWith<CborParseException> { parse(ByteArray(0)) }
    }

    @Test
    fun `rejects a length that claims more bytes than are present`() {
        assertFailsWith<CborParseException> {
            parse(Cbor.map(Cbor.text("x") to (Cbor.head(2, 1000) + byteArrayOf(1, 2, 3))))
        }
    }

    @Test
    fun `rejects encodings it deliberately does not support`() {
        // Indefinite length, a tag, and the reserved additional-info values.
        assertFailsWith<CborParseException> { parse(byteArrayOf((5 shl 5 or 31).toByte())) }
        assertFailsWith<CborParseException> { parse(Cbor.map(Cbor.text("t") to Cbor.head(6, 1))) }
        for (info in 28..30) {
            assertFailsWith<CborParseException>("additional info $info") {
                parse(Cbor.map(Cbor.text("x") to byteArrayOf((0 shl 5 or info).toByte())))
            }
        }
    }

    @Test
    fun `rejects an unsupported simple value`() {
        assertFailsWith<CborParseException> { parse(Cbor.map(Cbor.text("x") to byteArrayOf((7 shl 5 or 23).toByte()))) }
    }
}
