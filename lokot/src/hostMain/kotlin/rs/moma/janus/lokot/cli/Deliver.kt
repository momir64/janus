package rs.moma.janus.lokot.cli

import rs.moma.janus.lokot.files.RemoteDestination
import rs.moma.janus.lokot.files.LocalDestination
import rs.moma.janus.lokot.files.Destination
import rs.moma.janus.lokot.schema.Delivery
import rs.moma.janus.lokot.LokotException
import rs.moma.janus.lokot.externals.wipe
import rs.moma.janus.lokot.schema.Schema
import rs.moma.janus.lokot.files.Sftp

fun runUnlock(arguments: List<String>): Int {
    val initialising = arguments.any { it == "-i" || it == "--init" }
    val target = target(arguments) ?: return 1

    val destination = open(target) ?: return 1
    try {
        val unlocked = readVault(destination)?.let { unlockVault("use", it) } ?: return 1
        unlocked.kek.wipe()

        val schema = try {
            Schema.parse(unlocked.body.schema)
        } catch (failure: Exception) {
            println("The schema inside ${destination.vaultFile} will not parse: ${failure.message}")
            return 1
        }

        // Everything is checked before anything is written, so a refusal leaves no half-written root.
        schema.check(unlocked.values)?.let {
            println("${destination.vaultFile} does not match its schema: $it")
            println("Put it right with 'lokot edit'. Nothing was written.")
            return 1
        }

        val chosen = schema.deliveries.filter { initialising || !it.onlyAtInit }
        clear(destination) // an unlock leaves what the schema says now, and nothing it used to say
        if (!destination.makeDirectory(destination.root)) {
            println("Cannot create ${destination.root}, so there is nowhere to put the files.")
            return 1
        }

        chosen.groupBy { it.service }.forEach { (service, deliveries) ->
            if (!destination.makeDirectory("${destination.root}/$service")) {
                println("Cannot create ${destination.root}/$service.")
                return 1
            }
            deliveries.forEach { delivery ->
                if (!write(destination, delivery, unlocked.values.getValue(delivery.secret))) {
                    println("Cannot write ${destination.root}/${delivery.path}.")
                    return 1
                }
            }
        }

        val environment = if (schema.compose.isEmpty()) null
        else renderEnvironment(destination.root, schema.compose, unlocked.values)
        if (environment != null && !destination.write(destination.envFile, environment.encodeToByteArray())) {
            println("Cannot write ${destination.envFile}.")
            return 1
        }

        println()
        println("Wrote ${pluralize(chosen.size, "file")} under ${destination.root}")
        if (environment != null)
            println("Wrote ${pluralize(schema.compose.size + 1, "line")} to ${destination.envFile}")
        val held = schema.deliveries.size - chosen.size
        if (held > 0) println("${pluralize(held, "more file")} would be written by 'lokot unlock -i', on a first run.")
        println("Run 'lokot lock' when the containers are up; they read their files once, at start.")
        return 0
    } finally {
        destination.close()
    }
}

fun runLock(arguments: List<String>): Int {
    val target = target(arguments) ?: return 1
    val destination = open(target) ?: return 1
    try {
        val removed = clear(destination)
        val root = destination.root
        println(if (removed == 0) "Nothing to remove; $root is already gone." else "Removed $removed from $root.")

        // Only the one lokot wrote: an .env of someone's own is not lokot's to delete.
        if (destination.read(destination.envFile)
                ?.startsWith(GENERATED) == true && destination.delete(destination.envFile)
        )
            println("Removed ${destination.envFile}.")
        return 0
    } finally {
        destination.close()
    }
}

/** `user@host:/path/to/repo`, or nothing at all, which means this machine. */
class Target(val host: String, val directory: String)

private fun target(arguments: List<String>): Target? {
    val rest = arguments.filterNot { it == "-i" || it == "--init" }
    if (rest.size > 1) {
        println("One target at a time: ${rest.joinToString(" ")}")
        return null
    }
    val text = rest.firstOrNull() ?: return Target("", "")

    val separator = text.indexOf(':')
    if (separator <= 0 || separator == text.length - 1) {
        println("A target reads user@host:/path/to/repo, not '$text'.")
        return null
    }
    return Target(text.take(separator), text.drop(separator + 1))
}

private fun open(target: Target): Destination? {
    if (target.host.isEmpty()) return LocalDestination(ENV_FILE, VAULT_FILE)

    println("Connecting to ${target.host}. ssh will ask for whatever it needs.")
    val sftp = Sftp.connect(target.host) ?: run {
        println("Could not open an sftp session on ${target.host}.")
        return null
    }
    return RemoteDestination(sftp, sftp.uid(), target.directory, ENV_FILE, VAULT_FILE)
}

fun renderEnvironment(root: String, names: List<String>, values: Map<String, String>): String {
    val entries = listOf("LOKOT_DIR" to root) + names.map { it to values.getValue(it) }
    entries.forEach { (name, value) ->
        if (value.any { it == '\n' || it == '\r' })
            throw LokotException("'$name' spans more than one line, which a compose .env cannot hold.")
    }
    val header = """
        $GENERATED on ${utcNow()} - DO NOT EDIT
        # your changes will be overwritten on the next unlock
        # edit with 'lokot edit' instead
    """.trimIndent()
    return "$header\n\n" + entries.joinToString("\n") { (name, value) -> "$name=$value" } + "\n"
}

private const val GENERATED = "# generated by lokot"

private fun write(destination: Destination, delivery: Delivery, value: String): Boolean =
    destination.write("${destination.root}/${delivery.path}", (delivery.prefix + value).encodeToByteArray())

private fun clear(destination: Destination): Int {
    var removed = 0
    val root = destination.root
    destination.list(root).forEach { entry ->
        val path = "$root/$entry"
        destination.list(path).forEach { if (destination.delete("$path/$it")) removed++ }
        if (!destination.deleteDirectory(path) && destination.delete(path)) removed++
    }
    destination.deleteDirectory(root)
    return removed
}
