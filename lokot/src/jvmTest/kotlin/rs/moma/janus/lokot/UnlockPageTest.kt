package rs.moma.janus.lokot

import kotlin.test.assertTrue
import kotlin.test.Test

class UnlockPageTest {
    @Test
    fun base() {
        assertTrue(Lokot.page().contains("<base href=\"/\">"), "default")
        assertTrue(Lokot.page("/lokot").contains("<base href=\"/lokot/\">"), "no trailing slash")
        assertTrue(Lokot.page("/admin/vault/").contains("<base href=\"/admin/vault/\">"), "elsewhere")
        assertTrue(Lokot.page("/").contains("<base href=\"/\">"), "the root of its own host")
        assertTrue(Lokot.page().contains("fetch(\"challenge\")"), "relative fetch")
        assertTrue(runCatching { Lokot.page("https://elsewhere/") }.isFailure, "absolute refused")
    }

    @Test
    fun serverStillWaitsAndRedirects() {
        val page = Lokot.page()
        assertTrue(page.contains("location.replace(challenge.home || \"/\")"), "goes home once open")
        assertTrue(page.contains("if (!challenge.cli)"), "and a server never takes the CLI branch")
        assertTrue(page.contains("await waiting()"), "waits for the server to swap in")
        assertTrue(page.contains("response.status >= 500"), "a proxy's 5xx is not the new server")
        assertTrue(page.contains("navigator.credentials.get("), "asserts a key")
        assertTrue(page.contains("if (!challenge.enrol)"), "and only creates one when asked to")
    }
}
