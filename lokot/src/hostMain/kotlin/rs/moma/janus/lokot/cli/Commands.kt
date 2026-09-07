package rs.moma.janus.lokot.cli

import rs.moma.janus.lokot.externals.Authenticator
import rs.moma.janus.lokot.checks.allChecks
import rs.moma.janus.lokot.LokotException
import rs.moma.janus.lokot.io.readHidden

const val SCHEMA_FILE = "lokot.toml"
const val VAULT_FILE = ".env.lokot"

fun pluralize(count: Int, singular: String, plural: String = "${singular}s"): String =
    "$count ${if (count == 1) singular else plural}"

fun openAuthenticator(purpose: String): Authenticator? {
    val devices = Authenticator.devices()
    if (devices.isEmpty()) {
        println("No authenticator found. Plug one in.")
        return null
    }
    if (devices.size > 1) {
        println("More than one authenticator is connected. Leave the one you want to $purpose:")
        // Two of the same model report the same name, and then only the path tells them apart.
        val ambiguous = devices.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
        devices.forEach { println("  ${it.name}${if (it.name in ambiguous) "  ${it.path}" else ""}") }
        return null
    }

    val authenticator = Authenticator.open(devices.single())
    println("authenticator: ${authenticator.device.name}")
    return authenticator
}

fun runSelftest(): Int {
    var failures = 0
    var section = ""

    allChecks().forEach { check ->
        if (check.section != section) {
            section = check.section
            println(section)
        }
        val failure = check.run()
        if (failure == null) {
            println("[  OK  ]  ${check.name}")
        } else {
            failures++
            println("[ FAIL ]  ${check.name}")
            println("          $failure")
            check.remedy?.let { println("        $it") }
        }
    }

    println()
    return if (failures == 0) {
        println("all checks passed"); 0
    } else {
        println("$failures check(s) failed"); 1
    }
}

fun Authenticator.pin(): String? {
    if (isWindowsHello) return null
    return readHidden("PIN: ").takeUnless { it.isNullOrEmpty() } ?: throw LokotException("No PIN given.")
}
