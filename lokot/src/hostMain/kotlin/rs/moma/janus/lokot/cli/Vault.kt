package rs.moma.janus.lokot.cli

import rs.moma.janus.lokot.externals.Authenticator
import rs.moma.janus.lokot.externals.wipe
import rs.moma.janus.lokot.editor.Editor
import rs.moma.janus.lokot.io.readHidden
import rs.moma.janus.lokot.files.Files
import rs.moma.janus.lokot.files.*

class Unlocked(val file: LokotFile, val kek: ByteArray, val values: Map<String, String>)

fun runUnlock(): Int {
    val unlocked = unlockVault("use") ?: return 1
    unlocked.kek.wipe()

    println()
    println("Opened $VAULT_FILE with ${pluralize(unlocked.values.size, "value")}:")
    unlocked.values.keys.sorted().forEach { println("  $it") }
    return 0
}

fun unlockVault(purpose: String): Unlocked? {
    val file = readVault() ?: return null
    val header = file.header

    if (header.rpId != Authenticator.RP_ID) {
        println("$VAULT_FILE belongs to relying party '${header.rpId}', and this lokot is '${Authenticator.RP_ID}'.")
        return null
    }

    val authenticator = openAuthenticator(purpose) ?: return null
    val secret = try {
        val pin = if (authenticator.isWindowsHello) null else
            readHidden("PIN: ") ?: run { println("no PIN given"); return null }

        println()
        println("Touch the key to open ${header.project}.")

        authenticator.hmacSecret(pin, header.credentials.map { it.id }, header.salt)
    } finally {
        authenticator.close()
    }

    val credential = header.credentials.firstOrNull { it.id.contentEquals(secret.credentialId) } ?: run {
        println("The key that answered is not one of the ${header.credentials.size} enrolled here.")
        return null
    }
    val kek = Kek.unwrap(secret.output, credential) ?: run {
        println("That key is enrolled, but its wrapped key will not open. $VAULT_FILE looks damaged.")
        return null
    }
    val values = file.open(kek)
    if (values == null) {
        kek.wipe()
        println("$VAULT_FILE will not open: the body failed its authentication tag.")
        return null
    }
    return Unlocked(file, kek, values)
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
        var written = 0

        Editor(
            text = PlaintextFile.render(unlocked.values),
            check = { text ->
                try {
                    PlaintextFile.parse(text); null
                } catch (failure: DocumentException) {
                    failure.described
                }
            },
            save = { text ->
                try {
                    Files.writeBytes(VAULT_FILE, LokotFile.build(header, PlaintextFile.parse(text), kek))
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

