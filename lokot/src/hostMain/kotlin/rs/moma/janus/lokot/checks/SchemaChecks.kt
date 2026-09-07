package rs.moma.janus.lokot.checks

import rs.moma.janus.lokot.schema.SecretSpec.RandomChars
import rs.moma.janus.lokot.schema.SecretSpec
import rs.moma.janus.lokot.schema.Schema
import rs.moma.janus.lokot.schema.Toml

internal fun schemaChecks(): List<Check> {
    val schema = CheckGroup(SCHEMA_BUG)

    val document = """
        # a comment, and one with a "quote" in it
        project = "example"

        [secrets]
        POSTGRES_PASSWORD = { type = "random", chars = 24 }   # trailing comment
        JWT_SECRET        = { type = "random", bytes = 32, encoding = "base64" }
        DB_HMAC_SECRET    = { type = "random", bytes = 32, encoding = "hex" }
        POSTGRES_PORT     = { type = "port" }
        RESEND_API_KEY    = { type = "prompt", hint = "Resend API key" }
    """.trimIndent()

    fun parsed() = Schema.parse(document)
    fun spec(name: String) = parsed().secrets.getValue(name)

    fun doc(body: String) = "project = \"x\"\n[secrets]\n$body"

    return listOf(
        schema.equals("toml", "reads every declaration", 5) { parsed().secrets.size },
        schema.equals("schema", "reads the project name", "example") { parsed().project },
        schema.rejects("schema", "rejects a missing project") { Schema.parse(PROJECTLESS) },
        schema.rejects("schema", "rejects a project that is not a name") {
            Schema.parse(doc("A = { type = \"port\" }").replace("\"x\"", "\"a/b\""))
        },
        schema.holds("toml", "a '#' inside a string is not a comment") {
            Toml.parse("""a = "one # two"""").string("a") == "one # two"
        },
        schema.holds("toml", "nests dotted table headers") {
            Toml.parse("[certs.ca]\ncn = \"x\"").table("certs")?.table("ca")?.string("cn") == "x"
        },
        schema.holds("toml", "tolerates a UTF-8 BOM") {
            Toml.parse("\uFEFF[secrets]\nA = { type = \"port\" }").table("secrets") != null
        },
        schema.rejects("toml", "rejects arrays") { Toml.parse("a = [1, 2]") },
        schema.rejects("toml", "rejects arrays of tables") { Toml.parse("[[deliver]]") },
        schema.rejects("toml", "rejects multi-line strings") { Toml.parse("a = \"\"\"x\"\"\"") },
        schema.rejects("toml", "rejects floats") { Toml.parse("a = 1.5") },
        schema.rejects("toml", "rejects trailing rubbish") { Toml.parse("""a = "b" c""") },
        schema.rejects("toml", "rejects a duplicate key") { Toml.parse("a = 1\na = 2") },

        schema.rejects("schema", "rejects a lower-case name") {
            Schema.parse(doc("jwt = { type = \"random\", bytes = 32 }"))
        },
        schema.rejects("schema", "rejects an unknown type") {
            Schema.parse(doc("A = { type = \"magic\" }"))
        },
        schema.rejects("schema", "rejects bytes and chars together") {
            Schema.parse(doc("A = { type = \"random\", bytes = 32, chars = 24 }"))
        },
        schema.rejects("schema", "rejects random with neither") {
            Schema.parse(doc("A = { type = \"random\" }"))
        },
        schema.rejects("schema", "rejects a key that means nothing for the type") {
            Schema.parse(doc("A = { type = \"port\", bytes = 32 }"))
        },
        schema.rejects("schema", "rejects encoding on chars") {
            Schema.parse(doc("A = { type = \"random\", chars = 24, encoding = \"hex\" }"))
        },
        // Not "unknown table": these are planned, and the message should say so.
        schema.rejects("schema", "rejects tables that are not implemented yet") {
            Schema.parse(doc("A = { type = \"port\" }\n[certs.ca]\ncn = \"x\""))
        },

        schema.equals("generate", "bytes become base64 of the right length", 44) {
            spec("JWT_SECRET").generate()!!.length  // 32 bytes base64 with padding
        },
        schema.equals("generate", "hex encoding is honoured", 64) { spec("DB_HMAC_SECRET").generate()!!.length },
        schema.equals("generate", "chars are the requested count", 24) {
            spec("POSTGRES_PASSWORD").generate()!!.length
        },
        schema.holds("generate", "chars stay inside the alphabet") {
            spec("POSTGRES_PASSWORD").generate()!!.all { it in SecretSpec.ALPHABET }
        },
        schema.holds("generate", "chars are drawn evenly") {
            val counts = (1..200).flatMap { RandomChars(64).generate().toList() }.groupingBy { it }.eachCount()
            counts.size == SecretSpec.ALPHABET.length && counts.values.all { it in 100..300 }
        },
        schema.holds("generate", "ports land in the dynamic range") {
            val ports = (1..2000).map { spec("POSTGRES_PORT").generate()!!.toInt() }
            ports.all { it in SecretSpec.RandomPort.LOWEST..SecretSpec.RandomPort.HIGHEST } &&
                    ports.distinct().size > 1500
        },
        schema.holds("generate", "prompted values are not generated") { spec("RESEND_API_KEY").generate() == null },
        schema.holds("generate", "does not repeat") {
            spec("JWT_SECRET").generate() != spec("JWT_SECRET").generate()
        },
    )
}

