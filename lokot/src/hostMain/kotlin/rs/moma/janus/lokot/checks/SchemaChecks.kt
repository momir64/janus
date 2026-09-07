package rs.moma.janus.lokot.checks

import rs.moma.janus.lokot.schema.SecretSpec.RandomChars
import rs.moma.janus.lokot.schema.SecretSpec
import rs.moma.janus.lokot.schema.Schema
import rs.moma.janus.lokot.schema.Toml

internal fun schemaChecks(): List<Check> {
    val schema = CheckGroup(SCHEMA_BUG)
    val toml = schema.section("toml")
    val declaration = schema.section("schema")
    val delivery = schema.section("delivery")
    val compose = schema.section("compose")
    val generate = schema.section("generate")
    val values = schema.section("values")

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
    fun delivering(group: String) = Schema.parse(doc("A = { type = \"port\" }\n[deliver.pg]\n$group"))

    return listOf(
        toml.equals("reads every declaration", 5) { parsed().secrets.size },
        declaration.equals("reads the project name", "example") { parsed().project },
        declaration.rejects("rejects a missing project") { Schema.parse(PROJECTLESS) },
        declaration.rejects("rejects a project that is not a name") {
            Schema.parse(doc("A = { type = \"port\" }").replace("\"x\"", "\"a/b\""))
        },
        toml.holds("a '#' inside a string is not a comment") {
            Toml.parse("""a = "one # two"""").string("a") == "one # two"
        },
        toml.holds("nests dotted table headers") {
            Toml.parse("[certs.ca]\ncn = \"x\"").table("certs")?.table("ca")?.string("cn") == "x"
        },
        toml.holds("tolerates a UTF-8 BOM") {
            Toml.parse("\uFEFF[secrets]\nA = { type = \"port\" }").table("secrets") != null
        },
        toml.holds("reads a list on one line") {
            Toml.parse("""a = ["ONE", "TWO"]""").strings("a") == listOf("ONE", "TWO")
        },
        toml.holds("reads a list written down the page") {
            Toml.parse(
                "[compose]\nenv = [\n    \"A\", \n  \"B\",\n     \"C\"\n]\nafter = 1"
            ).table("compose")?.strings("env") == listOf("A", "B", "C")
        },
        toml.holds("a trailing comma ends a list") {
            Toml.parse("a = [\n  \"ONE\",\n  \"TWO\",\n]").strings("a") == listOf("ONE", "TWO")
        },
        toml.holds("a comment inside a list is not part of it") {
            Toml.parse("a = [   # names\n  \"ONE\",  # the first\n]").strings("a") == listOf("ONE")
        },
        toml.holds("a bracket inside a string does not open a list") {
            Toml.parse("""a = "one [ two"""").string("a") == "one [ two"
        },
        toml.rejects("rejects a list that is never closed") { Toml.parse("a = [\n  \"ONE\",\n") },
        toml.rejects("rejects a list where a name is not quoted") {
            Toml.parse("a = [ONE, TWO]").strings("a")
        },
        toml.rejects("rejects a list read as a single value") { Toml.parse("a = [1, 2]").string("a") },
        toml.rejects("rejects arrays of tables") { Toml.parse("[[deliver]]") },
        toml.rejects("rejects multi-line strings") { Toml.parse("a = \"\"\"x\"\"\"") },
        toml.rejects("rejects floats") { Toml.parse("a = 1.5") },
        toml.rejects("rejects trailing rubbish") { Toml.parse("""a = "b" c""") },
        toml.rejects("rejects a duplicate key") { Toml.parse("a = 1\na = 2") },

        declaration.rejects("rejects a lower-case name") {
            Schema.parse(doc("jwt = { type = \"random\", bytes = 32 }"))
        },
        declaration.rejects("rejects an unknown type") {
            Schema.parse(doc("A = { type = \"magic\" }"))
        },
        declaration.rejects("rejects bytes and chars together") {
            Schema.parse(doc("A = { type = \"random\", bytes = 32, chars = 24 }"))
        },
        declaration.rejects("rejects random with neither") {
            Schema.parse(doc("A = { type = \"random\" }"))
        },
        declaration.rejects("rejects a key that means nothing for the type") {
            Schema.parse(doc("A = { type = \"port\", bytes = 32 }"))
        },
        declaration.rejects("rejects encoding on chars") {
            Schema.parse(doc("A = { type = \"random\", chars = 24, encoding = \"hex\" }"))
        },
        // Not "unknown table": these are planned, and the message should say so.
        declaration.rejects("rejects tables that are not implemented yet") {
            Schema.parse(doc("A = { type = \"port\" }\n[certs.ca]\ncn = \"x\""))
        },

        delivery.holds("a secret in no group is written nowhere") {
            Schema.parse(doc("A = { type = \"port\" }")).deliveries.isEmpty()
        },
        delivery.holds("a group names the service, the secret and the file") {
            val deliver = delivering("A = \"port\"").deliveries.single()
            deliver.service == "pg" && deliver.secret == "A" && deliver.file == "port" &&
                    deliver.path == "pg/port" && deliver.prefix == ""
        },
        delivery.holds("'init' marks a whole group written only with -i") {
            delivering("init = true\nA = \"port\"").deliveries.single().onlyAtInit
        },
        delivery.holds("without 'init' a group is written on every unlock") {
            !delivering("A = \"port\"").deliveries.single().onlyAtInit
        },
        delivery.holds("a prefix is kept as written, spaces included") {
            delivering("A = { file = \"redis.conf\", prefix = \"port \" }").deliveries.single().prefix == "port "
        },
        delivery.holds("one service can take several secrets") {
            Schema.parse(
                doc("A = { type = \"port\" }\nB = { type = \"port\" }\n[deliver.pg]\nA = \"a\"\nB = \"b\"")
            ).deliveries.size == 2
        },
        delivery.rejects("rejects a secret that is not declared") { delivering("B = \"port\"") },
        delivery.rejects("rejects a key that is not a secret name") { delivering("port = \"a\"") },
        delivery.rejects("rejects two secrets writing the same file") {
            Schema.parse(doc("A = { type = \"port\" }\nB = { type = \"port\" }\n[deliver.pg]\nA = \"x\"\nB = \"x\""))
        },
        delivery.rejects("rejects a delivery with no file") { delivering("A = { prefix = \"x \" }") },
        delivery.rejects("rejects a key that means nothing in a delivery") {
            delivering("A = { file = \"a\", init = true }")
        },
        delivery.rejects("rejects a prefix with a newline") {
            delivering("A = { file = \"redis.conf\", prefix = \"port 1\\nrequirepass x\" }")
        },

        delivery.rejects("rejects a file name that climbs out") { delivering("A = \"../../etc/passwd\"") },
        delivery.rejects("rejects a dot-dot file name") { delivering("A = \"..\"") },
        delivery.rejects("rejects an absolute file name") { delivering("A = \"/etc/passwd\"") },
        delivery.rejects("rejects a nested file name") { delivering("A = \"a/b\"") },
        delivery.rejects("rejects a hidden file name") { delivering("A = \".ssh\"") },
        delivery.rejects("rejects upper case in a service name") {
            Schema.parse(doc("A = { type = \"port\" }\n[deliver.PG]\nA = \"port\""))
        },
        delivery.rejects("rejects a non-boolean 'init'") { delivering("init = \"yes\"\nA = \"port\"") },

        compose.holds("no [compose] means nothing goes to .env") { parsed().compose.isEmpty() },
        compose.holds("names reach .env in the order they are listed") {
            Schema.parse(
                doc("A = { type = \"port\" }\nB = { type = \"port\" }\n[compose]\nenv = [\n  \"B\",\n  \"A\",\n]")
            ).compose == listOf("B", "A")
        },
        compose.rejects("rejects a name that is not declared") {
            Schema.parse(doc("A = { type = \"port\" }\n[compose]\nenv = [\"B\"]"))
        },
        compose.rejects("rejects a name listed twice") {
            Schema.parse(doc("A = { type = \"port\" }\n[compose]\nenv = [\"A\", \"A\"]"))
        },
        compose.rejects("rejects a key that means nothing in [compose]") {
            Schema.parse(doc("A = { type = \"port\" }\n[compose]\nenv = [\"A\"]\nto = \"x\""))
        },
        compose.rejects("rejects [compose] with no env") {
            Schema.parse(doc("A = { type = \"port\" }\n[compose]"))
        },
        compose.rejects("rejects env that is not a list") {
            Schema.parse(doc("A = { type = \"port\" }\n[compose]\nenv = \"A\""))
        },

        values.holds("accepts what init would write") {
            val schema = parsed()
            schema.check(schema.secrets.mapValues { (_, spec) -> spec.generate() ?: "typed by hand" }) == null
        },
        values.holds("reports a declared name with no value") {
            parsed().check(mapOf("JWT_SECRET" to "x")) != null
        },
        values.holds("reports a value that was never declared") {
            val schema = Schema.parse(doc("A = { type = \"port\" }"))
            schema.check(mapOf("A" to "5432", "B" to "1")) != null
        },
        values.holds("reports an empty value") {
            Schema.parse(doc("A = { type = \"prompt\" }")).check(mapOf("A" to "")) != null
        },
        values.holds("reports a port that is not a number") {
            Schema.parse(doc("A = { type = \"port\" }")).check(mapOf("A" to "later")) != null
        },
        values.holds("takes a well-known port, not only the range lokot draws from") {
            Schema.parse(doc("A = { type = \"port\" }")).check(mapOf("A" to "5432")) == null
        },
        values.holds("reports bytes of the wrong length") {
            val schema = Schema.parse(doc("A = { type = \"random\", bytes = 32 }"))
            schema.check(mapOf("A" to "c2hvcnQ=")) != null && schema.check(mapOf("A" to "not base64 at all")) != null
        },
        values.holds("reads hex when the encoding says hex") {
            val schema = Schema.parse(doc("A = { type = \"random\", bytes = 32, encoding = \"hex\" }"))
            schema.check(mapOf("A" to "ab".repeat(32))) == null && schema.check(mapOf("A" to "ab".repeat(16))) != null
        },
        values.holds("reports characters of the wrong count") {
            val schema = Schema.parse(doc("A = { type = \"random\", chars = 24 }"))
            schema.check(mapOf("A" to "x".repeat(24))) == null && schema.check(mapOf("A" to "x".repeat(23))) != null
        },
        // The prefix cannot name a second secret, and now the value cannot forge a second line.
        values.holds("refuses a newline in a value that goes to a file") {
            Schema.parse(doc("A = { type = \"prompt\" }\n[deliver.pg]\nA = \"a\""))
                .check(mapOf("A" to "one\ntwo")) != null
        },
        values.holds("refuses a newline in a value that goes to .env") {
            Schema.parse(doc("A = { type = \"prompt\" }\n[compose]\nenv = [\"A\"]"))
                .check(mapOf("A" to "one\ntwo")) != null
        },
        values.holds("allows a newline in a value that goes nowhere") {
            Schema.parse(doc("A = { type = \"prompt\" }")).check(mapOf("A" to PEM)) == null
        },

        generate.equals("bytes become base64 of the right length", 44) {
            spec("JWT_SECRET").generate()!!.length  // 32 bytes base64 with padding
        },
        generate.equals("hex encoding is honoured", 64) { spec("DB_HMAC_SECRET").generate()!!.length },
        generate.equals("chars are the requested count", 24) {
            spec("POSTGRES_PASSWORD").generate()!!.length
        },
        generate.holds("chars stay inside the alphabet") {
            spec("POSTGRES_PASSWORD").generate()!!.all { it in SecretSpec.ALPHABET }
        },
        generate.holds("chars are drawn evenly") {
            val counts = (1..200).flatMap { RandomChars(64).generate().toList() }.groupingBy { it }.eachCount()
            counts.size == SecretSpec.ALPHABET.length && counts.values.all { it in 100..300 }
        },
        generate.holds("ports land in the dynamic range") {
            val ports = (1..2000).map { spec("POSTGRES_PORT").generate()!!.toInt() }
            ports.all { it in SecretSpec.RandomPort.LOWEST..SecretSpec.RandomPort.HIGHEST } &&
                    ports.distinct().size > 1500
        },
        generate.holds("prompted values are not generated") { spec("RESEND_API_KEY").generate() == null },
        generate.holds("does not repeat") {
            spec("JWT_SECRET").generate() != spec("JWT_SECRET").generate()
        },
    )
}

