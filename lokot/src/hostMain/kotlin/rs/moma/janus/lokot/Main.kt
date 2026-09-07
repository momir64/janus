package rs.moma.janus.lokot

import rs.moma.janus.lokot.externals.Authenticator
import kotlin.system.exitProcess
import rs.moma.janus.lokot.cli.*
import kotlin.native.Platform

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
fun main(args: Array<String>) {
    Authenticator.initialise(trace = args.contains("--trace"))

    when (args.firstOrNull()) {
        "init" -> exitProcess(runInit())
        "unlock" -> exitProcess(runUnlock())
        "add-key" -> exitProcess(runAddKey())
        "edit" -> exitProcess(runEdit())
        "selftest" -> exitProcess(runSelftest())
        else -> {
            println("usage: lokot <command> [options]\n")
            println("  init       create .env.lokot from lokot.toml, enrolling a passkey")
            println("  unlock     open .env.lokot with an enrolled passkey and list what it holds")
            println("  edit       change the values in .env.lokot, in a full-screen editor")
            println("  add-key    enrol another passkey, so losing one does not lose the vault")
            println("  selftest   check the crypto and file format against known answers\n")
            if (Platform.isDebugBinary)
                println("  --trace    log every CTAP exchange. WARNING: the log includes key material.\n")
            exitProcess(2)
        }
    }
}
