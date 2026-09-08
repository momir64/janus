package rs.moma.janus.lokot

import kotlin.test.assertTrue
import kotlin.test.Test

class UnlockPageTest {
    @Test
    fun base() {
        assertTrue(Lokot.page().contains("<base href=\"/lokot/\">"), "default")
        assertTrue(Lokot.page("/lokot").contains("<base href=\"/lokot/\">"), "no trailing slash")
        assertTrue(Lokot.page("/admin/vault/").contains("<base href=\"/admin/vault/\">"), "elsewhere")
        assertTrue(Lokot.page().contains("fetch(\"challenge\")"), "relative fetch")
        assertTrue(runCatching { Lokot.page("https://elsewhere/") }.isFailure, "absolute refused")
    }
}
