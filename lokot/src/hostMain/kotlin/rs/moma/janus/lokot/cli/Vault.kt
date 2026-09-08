package rs.moma.janus.lokot.cli

import rs.moma.janus.lokot.externals.Authenticator
import rs.moma.janus.lokot.externals.HmacSecret
import rs.moma.janus.lokot.externals.wipe
import rs.moma.janus.lokot.editor.Editor
import rs.moma.janus.lokot.schema.Schema
import rs.moma.janus.lokot.files.Files
import rs.moma.janus.lokot.files.*

class Unlocked(
    val file: LokotFile,
    val kek: ByteArray,
    val body: VaultBody,
    val secret: HmacSecret,
) {
    val values: Map<String, String> get() = body.values
}

fun unlockVault(purpose: String): Unlocked? {
    val file = readVault() ?: return null
    val header = file.header
    if (header.credentials.isEmpty()) {
        println("$VAULT_FILE has no keys enrolled at all, so nothing can open it.")
        return null
    }

    val families = header.credentials.groupBy { it.rpId }
    val order = families.keys.sortedWith(compareBy({ if (it == Authenticator.RP_ID) 0 else 1 }, { it }))

    val authenticator = openAuthenticator(purpose) ?: return null
    val secret = try {
        val pin = authenticator.pin()

        println()
        println("Touch the key to open ${header.project}.")

        answer(authenticator, pin, order, families, header.salt)
    } finally {
        authenticator.close()
    } ?: return null

    val credential = header.credentials.firstOrNull { it.id.contentEquals(secret.credentialId) } ?: run {
        println("The key that answered is not one of the ${header.credentials.size} enrolled here.")
        return null
    }
    val kek = Kek.unwrap(secret.output, credential) ?: run {
        println("That key is enrolled, but its wrapped key will not open. $VAULT_FILE looks damaged.")
        return null
    }
    val body = try {
        file.open(kek)
    } catch (failure: Exception) {
        kek.wipe()
        println("$VAULT_FILE opened, but its contents will not parse: ${failure.message}")
        return null
    }
    if (body == null) {
        kek.wipe()
        println("$VAULT_FILE will not open: the body failed its authentication tag.")
        return null
    }
    return Unlocked(file, kek, body, secret)
}

private fun answer(
    authenticator: Authenticator,
    pin: String?,
    order: List<String>,
    families: Map<String, List<WrappedCredential>>,
    salt: ByteArray,
): HmacSecret? {
    order.forEachIndexed { index, family ->
        if (index > 0) {
            println()
            println("No key answered for '${order[index - 1]}'. Touch one enrolled for '$family'.")
        }
        try {
            return authenticator.hmacSecret(pin, families.getValue(family).map { it.id }, salt, family)
        } catch (failure: Exception) {
            if (index == order.lastIndex) {
                println(failure.message ?: "no key answered")
                return null
            }
        }
    }
    return null
}

fun readVault(): LokotFile? {
    val bytes = Files.readBytes(VAULT_FILE) ?: run {
        println("No $VAULT_FILE here. Run 'lokot init' to create one.")
        return null
    }
    return try {
        LokotFile.parse(bytes)
    } catch (failure: Exception) {
        println("$VAULT_FILE: ${failure.message}")
        null
    }
}

fun runEdit(): Int {
    val unlocked = unlockVault("open the vault with") ?: return 1
    val kek = unlocked.kek
    try {
        val header = unlocked.file.header
        val schema = refreshedSchema(unlocked.body.schema)

        val declared = try {
            Schema.parse(schema)
        } catch (_: Exception) {
            null
        }
        var written = 0

        val filled = declared?.secrets.orEmpty()
            .filterKeys { it !in unlocked.values }
            .mapNotNull { (name, spec) -> spec.generate()?.let { name to it } } +
                declared?.issueCertificates(unlocked.values).orEmpty().toList()
        val stored = PlaintextFile.render(unlocked.values)

        Editor(
            text = if (filled.isEmpty()) stored else PlaintextFile.render(unlocked.values + filled),
            stored = stored,
            note = if (filled.isEmpty()) null
            else "generated ${filled.joinToString(", ") { it.first }}, new in $SCHEMA_FILE. ^S writes them.",
            check = { text ->
                try {
                    declared?.check(PlaintextFile.parse(text))
                } catch (failure: DocumentException) {
                    failure.described
                }
            },
            save = { text ->
                try {
                    val body = VaultBody(schema, PlaintextFile.parse(text))
                    Files.writeBytes(VAULT_FILE, LokotFile.build(header, body, kek))
                    written++
                    null
                } catch (failure: Exception) {
                    failure.message ?: "could not write $VAULT_FILE"
                }
            },
        ).run()

        println(if (written == 0) "Nothing written; $VAULT_FILE is as it was." else "Wrote $VAULT_FILE.")
        return 0
    } finally {
        kek.wipe()
    }
}

private fun refreshedSchema(stored: String): String {
    val text = Files.readText(SCHEMA_FILE) ?: return stored
    try {
        Schema.parse(text)
    } catch (failure: Exception) {
        println("$SCHEMA_FILE: ${failure.message}")
        println("Keeping the schema already in $VAULT_FILE. Fix $SCHEMA_FILE and edit again.")
        return stored
    }
    if (text != stored) println("Taking the schema from $SCHEMA_FILE; it differs from the one in $VAULT_FILE.")
    return text
}

