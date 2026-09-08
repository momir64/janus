package rs.moma.janus.lokot.cli

import rs.moma.janus.lokot.externals.Authenticator
import rs.moma.janus.lokot.externals.HmacSecret
import rs.moma.janus.lokot.externals.wipe
import rs.moma.janus.lokot.editor.Editor
import rs.moma.janus.lokot.schema.Schema
import rs.moma.janus.lokot.files.*

internal class Unlocked(
    val file: LokotFile,
    val kek: ByteArray,
    val body: VaultBody,
    val secret: HmacSecret,
) {
    val values: Map<String, String> get() = body.values.asText()
}

internal inline fun withVault(
    arguments: List<String>,
    purpose: String,
    prefer: String = Authenticator.RP_ID,
    action: (Destination, Unlocked) -> Int,
): Int {
    val destination = open(target(arguments) ?: return 1) ?: return 1
    try {
        val unlocked = readVault(destination)?.let { unlockVault(purpose, it, prefer) } ?: return 1
        return action(destination, unlocked)
    } finally {
        destination.close()
    }
}

internal fun unlockVault(purpose: String, file: LokotFile, prefer: String = Authenticator.RP_ID): Unlocked? {
    val header = file.header
    if (header.credentials.isEmpty()) {
        println("$VAULT_FILE has no keys enrolled at all, so nothing can open it.")
        return null
    }

    val families = header.credentials.groupBy { it.rpId }
    val order = families.keys.sortedWith(compareBy({ if (it == prefer) 0 else 1 }, { it }))

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

internal fun readVault(destination: Destination): LokotFile? =
    readVault(destination.vaultFile, destination.readBytes(destination.vaultFile))

private fun readVault(path: String, bytes: ByteArray?): LokotFile? {
    if (bytes == null) {
        println("No $path there. Run 'lokot init' to create one.")
        return null
    }
    return try {
        LokotFile.parse(bytes)
    } catch (failure: Exception) {
        println("$path: ${failure.message}")
        null
    }
}

fun runEdit(arguments: List<String>) = withVault(arguments, "open the vault with") { destination, unlocked ->
    val kek = unlocked.kek
    try {
        val header = unlocked.file.header
        val schema = refreshedSchema(destination, unlocked.body.schema)

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
                    val body = VaultBody(schema, PlaintextFile.parse(text).asChars())
                    destination.write(destination.vaultFile, LokotFile.build(header, body, kek))
                    written++
                    null
                } catch (failure: Exception) {
                    failure.message ?: "could not write ${destination.vaultFile}"
                }
            },
        ).run()

        val vault = destination.vaultFile
        println(if (written == 0) "Nothing written; $vault is as it was." else "Wrote $vault.")
        0
    } finally {
        kek.wipe()
    }
}

private fun refreshedSchema(destination: Destination, stored: String): String {
    val schema = destination.schemaFile
    val text = destination.read(schema) ?: return stored
    try {
        Schema.parse(text)
    } catch (failure: Exception) {
        println("$schema: ${failure.message}")
        println("Keeping the schema already in ${destination.vaultFile}. Fix $schema and edit again.")
        return stored
    }
    if (text != stored) println("Taking the schema from $schema; it differs from the vault's.")
    return text
}

