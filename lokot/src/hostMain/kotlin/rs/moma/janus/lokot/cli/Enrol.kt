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

private const val authRpId = Authenticator.RP_ID

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

    val declared = Files.readText(SCHEMA_FILE)!!
    val schema = try {
        Schema.parse(declared)
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
        val pin = authenticator.pin()

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

        schema.check(values)?.let {
            println()
            println("$it; nothing written.")
            return 1
        }

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
            credentials = listOf(Kek.wrap(secret.output, secret.credentialId, authRpId, kek)),
        )

        Files.writeBytes(VAULT_FILE, LokotFile.build(header, VaultBody(declared, values), kek))
        kek.wipe()

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
            val pin = authenticator.pin()

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
            project = header.project, salt = header.salt, credentials = header.credentials + Kek.wrap(
                enrolment.output, enrolment.credentialId, authRpId, kek
            )
        )
        Files.writeBytes(VAULT_FILE, LokotFile.build(extended, unlocked.body, kek))

        println()
        println("$VAULT_FILE now opens with ${pluralize(extended.credentials.size, "key")}.")
        return 0
    } finally {
        kek.wipe()
    }
}

fun runRekey(): Int {
    val unlocked = unlockVault("rekey with") ?: return 1
    unlocked.kek.wipe()
    val header = unlocked.file.header

    val kek = Crypto.randomBytes(Crypto.KEY_SIZE)
    try {
        val kept = mutableListOf(Kek.wrap(unlocked.secret.output, unlocked.secret.credentialId, authRpId, kek))
        var remaining = header.credentials.filterNot { it.id.contentEquals(unlocked.secret.credentialId) }

        while (remaining.isNotEmpty()) {
            println()
            println(
                "${pluralize(kept.size, "key")} kept so far. ${pluralize(remaining.size, "other key")} still enrolled."
            )
            println("Connect one and press Enter to keep it, or type 'done' to drop the rest.")
            if (readlnOrNull()?.trim()?.lowercase() == "done") break

            val authenticator = openAuthenticator("keep") ?: return 1
            val secret = try {
                println()
                println("Touch the key to keep it.")
                authenticator.hmacSecret(authenticator.pin(), remaining.map { it.id }, header.salt)
            } finally {
                authenticator.close()
            }

            kept += Kek.wrap(secret.output, secret.credentialId, authRpId, kek)
            remaining = remaining.filterNot { it.id.contentEquals(secret.credentialId) }
        }

        if (remaining.isNotEmpty()) {
            println()
            println("${pluralize(remaining.size, "key")} will lose access to $VAULT_FILE, permanently.")
            print("Type 'yes' to continue: ")
            if (readlnOrNull()?.trim()?.lowercase() != "yes") {
                println("Nothing written; $VAULT_FILE is as it was.")
                return 1
            }
        }

        val rekeyed = LokotHeader(project = header.project, salt = header.salt, credentials = kept)
        Files.writeBytes(VAULT_FILE, LokotFile.build(rekeyed, unlocked.body, kek))

        println()
        println("$VAULT_FILE re-keyed. ${pluralize(kept.size, "key")} opens it; ${remaining.size} dropped.")
        return 0
    } finally {
        kek.wipe()
    }
}
