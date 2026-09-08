package rs.moma.janus.lokot.cli

import rs.moma.janus.lokot.externals.Authenticator
import rs.moma.janus.lokot.io.CONNECT_NEXT_KEY
import rs.moma.janus.lokot.schema.SecretSpec
import rs.moma.janus.lokot.externals.Crypto
import rs.moma.janus.lokot.externals.wipe
import rs.moma.janus.lokot.io.readHidden
import rs.moma.janus.lokot.schema.Schema
import rs.moma.janus.lokot.files.*

private const val authRpId = Authenticator.RP_ID

fun runInit(arguments: List<String>): Int {
    val rpId = relyingParty(arguments, "init") ?: return 1
    val destination = open(target(arguments) ?: return 1) ?: return 1

    if (destination.readBytes(destination.vaultFile) != null) {
        println("${destination.vaultFile} already exists. Use 'lokot edit', or delete it to start over.")
        println("Deleting it loses every secret inside. There is no recovery.")
        destination.close()
        return 1
    }
    val declared = destination.read(destination.schemaFile) ?: run {
        println("No ${destination.schemaFile} there. lokot needs one to know what the vault should contain.")
        destination.close()
        return 1
    }

    val schema = try {
        Schema.parse(declared)
    } catch (failure: Exception) {
        println("${destination.schemaFile}: ${failure.message}")
        destination.close()
        return 1
    }
    if (schema.secrets.isEmpty()) {
        println("${destination.schemaFile} declares no secrets.")
        destination.close()
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

        val certificates = schema.issueCertificates(values)
        if (certificates.isNotEmpty()) {
            println()
            println("Issuing ${pluralize(schema.authority.leaves.size, "certificate")} from a new CA.")
            println("The CA key signs them and is then gone; it is never written anywhere.")
            values += certificates
        }

        schema.check(values)?.let {
            println()
            println("$it; nothing written.")
            return 1
        }

        val salt = Crypto.randomBytes(LokotHeader.SALT_SIZE)

        println()
        println("Touch the key to enrol.")
        val enrolment = authenticator.enrol(pin, schema.project, salt, rpId)

        val secret = enrolment.secret ?: run {
            println("Touch again to derive the key.")
            authenticator.hmacSecret(pin, listOf(enrolment.credentialId), salt, rpId)
        }

        val kek = Crypto.randomBytes(Crypto.KEY_SIZE)
        val header = LokotHeader(
            project = schema.project,
            salt = salt,
            credentials = listOf(Kek.wrap(secret.output, secret.credentialId, rpId, kek)),
        )

        val wrote = writeVault(destination, LokotFile.build(header, VaultBody(declared, values.asChars()), kek))
        kek.wipe()
        if (!wrote) return 1

        println()
        println("Wrote ${pluralize(values.size, "value")} to ${destination.vaultFile}")
        println()
        println("Enrol a second key with 'lokot add-key' before you rely only on this one:")
        println("lose it and the file cannot be opened again, by anyone, ever.")
        return 0
    } finally {
        authenticator.close()
        destination.close()
    }
}

fun runAddKey(arguments: List<String>): Int {
    val rpId = relyingParty(arguments, "add-key") ?: return 1
    val browserFamily = rpId != Authenticator.RP_ID

    return withVault(arguments, "open the vault with") { destination, unlocked ->
        val kek = unlocked.kek
        try {
            val header = unlocked.file.header

            println()
            if (browserFamily) {
                println("The next key is enrolled for '$rpId', so a browser at that origin can use it.")
                println("'lokot unlock' tries lokot's own keys first, and this one only if none answers.")
                println()
            }
            println(CONNECT_NEXT_KEY)
            readlnOrNull() ?: return 1

            val authenticator = openAuthenticator("add") ?: return 1
            val enrolment = try {
                val pin = authenticator.pin()

                println()
                println("Touch the key to enrol.")

                val enrolment = authenticator.enrol(pin, header.project, header.salt, rpId)
                enrolment.secret ?: run {
                    println("Touch again to derive the key.")
                    authenticator.hmacSecret(pin, listOf(enrolment.credentialId), header.salt, rpId)
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
                    enrolment.output, enrolment.credentialId, rpId, kek
                )
            )
            if (!writeVault(destination, LokotFile.build(extended, unlocked.body, kek))) return 1

            println()
            println("${destination.vaultFile} now opens with ${pluralize(extended.credentials.size, "key")}.")
            if (browserFamily) println("${extended.credentialsFor(rpId).size} of them for '$rpId'.")
            0
        } finally {
            kek.wipe()
        }
    }
}

fun runRekey(arguments: List<String>): Int {
    val rpId = relyingParty(arguments, "rekey") ?: return 1
    return withVault(arguments, "rekey with", rpId) { destination, unlocked ->
        unlocked.kek.wipe()
        val header = unlocked.file.header

        val kek = Crypto.randomBytes(Crypto.KEY_SIZE)
        try {
            val opened = header.credentials.first { it.id.contentEquals(unlocked.secret.credentialId) }
            val kept = mutableListOf(Kek.wrap(unlocked.secret.output, opened.id, opened.rpId, kek))
            var remaining = header.credentials.filterNot { it.id.contentEquals(unlocked.secret.credentialId) }

            while (remaining.isNotEmpty()) {
                val family = remaining.first().rpId
                val group = remaining.filter { it.rpId == family }

                println()
                println(
                    "${pluralize(kept.size, "key")} kept so far. ${
                        pluralize(
                            remaining.size,
                            "other key"
                        )
                    } still enrolled."
                )
                if (family != authRpId)
                    println("${pluralize(group.size, "of them")} enrolled for '$family', which a browser uses.")
                println("Connect one and press Enter to keep it, or type 'done' to drop the rest.")
                if (readlnOrNull()?.trim()?.lowercase() == "done") break

                val authenticator = openAuthenticator("keep") ?: return 1
                val secret = try {
                    println()
                    println("Touch the key to keep it.")
                    authenticator.hmacSecret(authenticator.pin(), group.map { it.id }, header.salt, family)
                } finally {
                    authenticator.close()
                }

                kept += Kek.wrap(secret.output, secret.credentialId, family, kek)
                remaining = remaining.filterNot { it.id.contentEquals(secret.credentialId) }
            }

            if (remaining.isNotEmpty()) {
                println()
                println("${pluralize(remaining.size, "key")} will lose access to $VAULT_FILE, permanently.")
                print("Type 'yes' to continue: ")
                if (readlnOrNull()?.trim()?.lowercase() != "yes") {
                    println("Nothing written; ${destination.vaultFile} is as it was.")
                    return 1
                }
            }

            val rekeyed = LokotHeader(project = header.project, salt = header.salt, credentials = kept)
            if (!writeVault(destination, LokotFile.build(rekeyed, unlocked.body, kek))) return 1

            println()
            println(
                "${destination.vaultFile} re-keyed. ${
                    pluralize(
                        kept.size,
                        "key"
                    )
                } opens it; ${remaining.size} dropped."
            )
            0
        } finally {
            kek.wipe()
        }
    }
}

private fun relyingParty(arguments: List<String>, command: String): String? {
    val flag = arguments.indexOfFirst { it == "--rp" }
    if (flag < 0) return Authenticator.RP_ID

    val rpId = arguments.getOrNull(flag + 1) ?: run {
        println("--rp needs the relying party id, like --rp kredenac.moma.rs ('lokot $command')")
        return null
    }
    if (!RELYING_PARTY.matches(rpId)) {
        println("A relying party id is a host name, like kredenac.moma.rs, not '$rpId'.")
        return null
    }
    return rpId
}

private val RELYING_PARTY = Regex("[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*")