package rs.moma.janus.lokot.checks

import rs.moma.janus.lokot.files.DocumentException
import rs.moma.janus.lokot.files.PlaintextFile

private const val DOCUMENT_BUG = "There's a code bug in the vault's text format, not an environment issue."
internal fun documentChecks(): List<Check> {
    val document = CheckGroup(DOCUMENT_BUG)
    val quotes = "\"".repeat(3)

    fun failure(text: String): String = try {
        PlaintextFile.parse(text); "accepted"
    } catch (bad: DocumentException) {
        bad.described
    }

    return listOf(
        document.equals("document", "one value per line", "A = one\nB = two") {
            PlaintextFile.render(mapOf("A" to "one", "B" to "two"))
        },
        document.equals("document", "names line up", "SHORT  = 1\nLONGER = 2") {
            PlaintextFile.render(mapOf("SHORT" to "1", "LONGER" to "2"))
        },
        document.holds("document", "a multi-line value becomes an indented block") {
            val rendered = PlaintextFile.render(mapOf("CERT" to PEM))
            rendered.startsWith("CERT = $quotes\n    -----BEGIN") && rendered.endsWith("\n    $quotes")
        },
        document.holds("document", "a block round trips through its indentation") {
            PlaintextFile.parse(PlaintextFile.render(mapOf("CERT" to PEM)))["CERT"] == PEM
        },
        document.holds("document", "a block can be re-indented by hand and still read the same") {
            val byHand = "CERT = $quotes\n        line one\n        line two\n        $quotes"
            PlaintextFile.parse(byHand)["CERT"] == "line one\nline two"
        },
        document.equals("document", "whitespace around a value is not part of it", "padded") {
            PlaintextFile.parse(PlaintextFile.render(mapOf("PAD" to "  padded  "))).getValue("PAD")
        },
        document.equals("document", "surrounding whitespace on a plain line is not part of the value", "v") {
            PlaintextFile.parse("   A   =   v   ").getValue("A")
        },
        document.holds("document", "comments and blank lines are skipped") {
            PlaintextFile.parse("# a note\n\nA = 1\n") == mapOf("A" to "1")
        },
        document.holds("document", "an empty value is allowed") { PlaintextFile.parse("A =") == mapOf("A" to "") },
        document.holds("document", "a value may contain an equals sign") {
            PlaintextFile.parse("A = b=c").getValue("A") == "b=c"
        },

        document.equals("static check", "names the line with no '='", "line 2: this is not a 'NAME = value' line") {
            failure("A = 1\nrubbish")
        },
        document.equals("static check", "names an unclosed block", "line 2: this $quotes is never closed") {
            failure("A = 1\nB = $quotes\nstill going")
        },
        document.equals("static check", "names a repeated name", "line 3: 'A' is set more than once") {
            failure("A = 1\nB = 2\nA = 3")
        },
        document.equals("static check", "names a name it cannot use", "line 1: there is no name before the '='") {
            failure(" = 1")
        },
        document.holds("static check", "a good document reports nothing") {
            failure("A = 1\nB = $quotes\n  x\n  $quotes") == "accepted"
        },

        document.rejects("document", "refuses a value that would close its own block") {
            PlaintextFile.render(mapOf("A" to "one\n$quotes\ntwo"))
        },
        document.rejects("document", "refuses a name that is not a name") { PlaintextFile.render(mapOf("a b" to "1")) },
    )
}
