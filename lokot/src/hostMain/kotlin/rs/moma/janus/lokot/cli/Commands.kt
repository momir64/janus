package rs.moma.janus.lokot.cli

import rs.moma.janus.lokot.externals.Authenticator
import rs.moma.janus.lokot.browser.BROWSER_OPTION
import kotlin.experimental.ExperimentalNativeApi
import rs.moma.janus.lokot.io.withRawTerminal
import rs.moma.janus.lokot.checks.allChecks
import rs.moma.janus.lokot.externals.Device
import rs.moma.janus.lokot.LokotException
import rs.moma.janus.lokot.io.readHidden
import kotlin.native.OsFamily
import kotlin.native.Platform
import kotlinx.cinterop.*
import platform.posix.*

const val SCHEMA_FILE = "lokot.toml"
const val VAULT_FILE = ".env.lokot"
const val ENV_FILE = ".env"

internal fun choose(question: String, options: List<String>): String? = withRawTerminal {
    var at = 0
    write("$question\r\n")
    while (true) {
        options.forEachIndexed { index, option -> write(" ${if (index == at) ">" else " "} $option\r\n") }
        when (readKey()) {
            is Key.Up -> at = (at + options.size - 1) % options.size
            is Key.Down -> at = (at + 1) % options.size
            is Key.Enter -> return@withRawTerminal options[at]
            is Key.Escape, is Key.EndOfInput -> return@withRawTerminal null
            else -> {}
        }
        write("\u001b[${options.size}A")
    }
    @Suppress("UNREACHABLE_CODE") null
}

@OptIn(ExperimentalForeignApi::class)
private fun write(text: String) {
    print(text)
    fflush(stdout)
}

fun pluralize(count: Int, singular: String, plural: String = "${singular}s"): String =
    "$count ${if (count == 1) singular else plural}"

sealed interface Chosen {
    class Key(val authenticator: Authenticator) : Chosen
    data object Browser : Chosen
}

fun chooseAuthenticator(question: String, browser: Boolean = false): Chosen? {
    val devices = Authenticator.devices()

    windowsHello(devices)?.let {
        println("authenticator: ${it.device.name}")
        return Chosen.Key(it)
    }

    val browserList = if (browser) listOf(BROWSER_OPTION) else emptyList()
    val ambiguous = devices.groupingBy { it.name }.eachCount().filterValues { it > 1 }.keys
    val labels = devices.map { "${it.name}${if (it.name in ambiguous) "  ${it.path}" else ""}" } + browserList

    if (labels.isEmpty()) {
        println("No authenticator found. Plug one in.")
        return null
    }

    val label = if (labels.size == 1) labels.single() else choose(question, labels) ?: run {
        println("Nothing chosen.")
        return null
    }

    if (label == BROWSER_OPTION) return Chosen.Browser

    val authenticator = Authenticator.open(devices[labels.indexOf(label)])
    println("authenticator: ${authenticator.device.name}")
    return Chosen.Key(authenticator)
}

@OptIn(ExperimentalNativeApi::class)
private fun windowsHello(devices: List<Device>): Authenticator? {
    if (Platform.osFamily != OsFamily.WINDOWS) return null
    for (device in devices) {
        val opened = runCatching { Authenticator.open(device) }.getOrNull() ?: continue
        if (opened.isWindowsHello) return opened
        opened.close()
    }
    return null
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

@OptIn(ExperimentalForeignApi::class)
fun utcNow(): String = memScoped {
    val seconds = alloc<time_tVar>()
    time(seconds.ptr)
    val moment = gmtime(seconds.ptr) ?: return@memScoped "an unknown time"
    val text = allocArray<ByteVar>(32)
    strftime(text, 32u, "%Y-%m-%dT%H:%M:%SZ", moment)
    text.toKString()
}
