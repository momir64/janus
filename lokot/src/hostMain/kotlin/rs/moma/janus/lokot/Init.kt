package rs.moma.janus.lokot

const val SCHEMA_FILE = "lokot.toml"
const val VAULT_FILE = ".env.lokot"

private fun pluralize(count: Int, singular: String, plural: String = "${singular}s"): String =
    "$count ${if (count == 1) singular else plural}"

fun runInit(): Int {
    if (!Files.exists(SCHEMA_FILE)) {
        println("No $SCHEMA_FILE here. lokot needs one to know what the vault should contain.")
        return 1
    }
    if (Files.exists(VAULT_FILE)) {
        println("$VAULT_FILE already exists. Use 'lokot edit' to change it, or delete it to start over.")
        println("Deleting it loses every secret inside — there is no recovery.")
        return 1
    }

    val schema = try {
        Schema.parse(Files.readText(SCHEMA_FILE)!!)
    } catch (failure: Exception) {
        println("$SCHEMA_FILE: ${failure.message}")
        return 1
    }
    if (schema.secrets.isEmpty()) {
        println("$SCHEMA_FILE declares no secrets.")
        return 1
    }

    val devices = Authenticator.devices()
    if (devices.isEmpty()) {
        println("No authenticator found. Plug one in.")
        return 1
    }
    if (devices.size > 1) {
        println("More than one authenticator is connected. Leave the one you want to enrol:")
        // Two of the same model report the same name, and then only the path tells them apart.
        val ambiguous = devices.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        devices.forEach { println("  ${it.name}${if (it.name in ambiguous) "  ${it.path}" else ""}") }
        return 1
    }

    val authenticator = Authenticator.open(devices.single())
    try {
        println("authenticator: ${authenticator.device.name}")

        val pin = if (authenticator.isWindowsHello) null else
            readHidden("PIN: ") ?: run { println("no PIN given"); return 1 }

        val values = mutableMapOf<String, String>()
        val prompted = schema.secrets.filterValues { it is SecretSpec.Prompted }
        if (prompted.isNotEmpty()) {
            println()
            println("${pluralize(prompted.size, "value")} require your input:")
            prompted.forEach { (name, spec) ->
                val hint = (spec as SecretSpec.Prompted).hint
                val entered = readHidden("  $name${hint?.let { " ($it)" } ?: ""}: ")
                if (entered.isNullOrEmpty()) {
                    println("  $name is empty; nothing written."); return 1
                }
                values[name] = entered
            }
        }

        schema.secrets.forEach { (name, spec) -> spec.generate()?.let { values[name] = it } }

        val salt = Crypto.randomBytes(LokotHeader.SALT_SIZE)

        println()
        println("Touch the key to enrol.")
        val enrolment = authenticator.enrol(pin, schema.project, salt)

        val secret = enrolment.secret ?: run {
            println("Touch again to derive the key.")
            authenticator.hmacSecret(pin, listOf(enrolment.credentialId), salt)
        }

        val kek = Crypto.randomBytes(Crypto.KEY_SIZE)
        val header = LokotHeader(
            project = schema.project,
            salt = salt,
            rpId = Authenticator.RP_ID,
            credentials = listOf(Kek.wrap(secret.output, secret.credentialId, kek)),
        )

        Files.writeBytes(VAULT_FILE, LokotFile.build(header, values, kek))
        kek.wipe()
        values.values.forEach { it.encodeToByteArray().wipe() }

        println()
        println("Wrote ${pluralize(values.size, "value")} to $VAULT_FILE")
        println()
        println("Enrol a second key with 'lokot add-key' before you rely only on this one:")
        println("lose it and the file cannot be opened again, by anyone, ever.")
        return 0
    } finally {
        authenticator.close()
    }
}
