package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.services.EmailService
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

class EmailTemplateTest {
    private val emails = EmailService("test-key", "kredenac@moma.rs", "https://kredenac.moma.rs")

    private fun assertNoPlaceholdersLeft(html: String) =
        assertFalse(html.contains("{{"), "an unfilled placeholder survived rendering")

    @Test
    fun testMagicLinkCarriesTheLinkAndTheHeading() {
        val html = emails.render(
            "magic-link", "Finish signing up for Kredenac",
            mapOf("intro" to "Click the link below.", "link" to "https://kredenac.moma.rs/verify/abc")
        )

        assertNoPlaceholdersLeft(html)
        assertTrue(html.contains("href=\"https://kredenac.moma.rs/verify/abc\""), "the link is not in the markup")
        assertTrue(html.contains("Finish signing up for Kredenac"), "the heading is missing")
    }

    @Test
    fun testEveryTemplateRendersWithoutLeftovers() {
        val rows = listOf("Device" to "Windows", "Time" to "21:33 2.9.2026.")

        assertNoPlaceholdersLeft(emails.render("passkey-change", "subject", mapOf("action" to "added to"), rows))
        assertNoPlaceholdersLeft(emails.render("passkey-disabled", "subject", rows = rows))
        assertNoPlaceholdersLeft(emails.render("account-deleted", "subject"))
    }

    @Test
    fun testEachClientDetailBecomesItsOwnRow() {
        fun rowCount(html: String) = Regex("<tr[ >]").findAll(html).count()

        val bare = emails.render("passkey-change", "subject", mapOf("action" to "added to"))
        val html = emails.render(
            "passkey-change", "subject", mapOf("action" to "added to"),
            rows = listOf("Device" to "Windows", "Browser" to "Firefox", "IP address" to "203.0.113.7")
        )

        assertEquals(3, rowCount(html) - rowCount(bare))
        assertTrue(html.contains("203.0.113.7"), "a detail value is missing")
        assertTrue(html.contains("Browser"), "a detail label is missing")
    }

    // The IP and location come from proxy headers, so they would be attacker-controlled if host is directly reachable.
    @Test
    fun testValuesCannotSmuggleMarkupIntoTheEmail() {
        val html = emails.render(
            "passkey-change", "subject", mapOf("action" to "added to"),
            rows = listOf("Location" to "<script>alert(1)</script>")
        )

        assertFalse(html.contains("<script>"), "a value was injected as live markup")
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"), "the value was not escaped")
    }
}
