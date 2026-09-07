package rs.moma.janus.lokot.cli

import rs.moma.janus.lokot.externals.Authenticator
import rs.moma.janus.lokot.io.CONNECT_NEXT_KEY
import rs.moma.janus.lokot.schema.SecretSpec
import rs.moma.janus.lokot.externals.Crypto
import rs.moma.janus.lokot.externals.wipe
import rs.moma.janus.lokot.io.readHidden
import rs.moma.janus.lokot.schema.Schema
import rs.moma.janus.lokot.files.Files
import rs.moma.janus.lokot.files.*

fun runInit(): Int {
    if (!Files.exists(SCHEMA_FILE)) {
        println("No $SCHEMA_FILE here. lokot needs one to know what the vault should contain.")
        return 1
    }
    if (Files.exists(VAULT_FILE)) {
        println("$VAULT_FILE already exists. Use 'lokot edit' to change it, or delete it to start over.")
        println("Deleting it loses every secret inside. There is no recovery.")
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

    val authenticator = openAuthenticator("enrol") ?: return 1
    try {
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

fun runAddKey(): Int {
    val unlocked = unlockVault("open the vault with") ?: return 1
    val kek = unlocked.kek
    try {
        val header = unlocked.file.header

        println()
        println(CONNECT_NEXT_KEY)
        readlnOrNull() ?: return 1

        val authenticator = openAuthenticator("add") ?: return 1
        val enrolment = try {
            val pin = if (authenticator.isWindowsHello) null else
                readHidden("PIN: ") ?: run { println("no PIN given"); return 1 }

            println()
            println("Touch the key to enrol.")

            val enrolment = authenticator.enrol(pin, header.project, header.salt)
            enrolment.secret ?: run {
                println("Touch again to derive the key.")
                authenticator.hmacSecret(pin, listOf(enrolment.credentialId), header.salt)
            }
        } finally {
            authenticator.close()
        }

        if (header.credentials.any { it.id.contentEquals(enrolment.credentialId) }) {
            println("That credential is already enrolled; nothing written.")
            return 1
        }

        // The header is associated data, so adding a credential means resealing the body too.
        val extended = LokotHeader(
            project = header.project, salt = header.salt, rpId = header.rpId,
            credentials = header.credentials + Kek.wrap(enrolment.output, enrolment.credentialId, kek),
        )
        Files.writeBytes(VAULT_FILE, LokotFile.build(extended, unlocked.values, kek))

        println()
        println("$VAULT_FILE now opens with ${pluralize(extended.credentials.size, "key")}.")
        return 0
    } finally {
        kek.wipe()
    }
}
