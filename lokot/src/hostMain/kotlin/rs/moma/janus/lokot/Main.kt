package rs.moma.janus.lokot

import rs.moma.janus.lokot.externals.Authenticator
import kotlin.system.exitProcess
import rs.moma.janus.lokot.cli.*
import kotlin.native.Platform

class LokotException(message: String) : Exception(message)

fun main(args: Array<String>) {
    Authenticator.initialise(trace = args.contains("--trace"))
    exitProcess(
        try {
            run(args)
        } catch (failure: LokotException) {
            println(failure.message); 1
        }
    )
}

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
private fun run(args: Array<String>): Int = when (args.firstOrNull()) {
    "init" -> runInit(args.drop(1))
    "unlock" -> runUnlock(args.drop(1))
    "lock" -> runLock(args.drop(1))
    "add-key" -> runAddKey(args.drop(1))
    "rekey" -> runRekey(args.drop(1))
    "edit" -> runEdit(args.drop(1))
    "selftest" -> runSelftest()
    else -> {
        println("usage: lokot <command> [options]")
        println()
        println("  init       create .env.lokot from lokot.toml, enrolling a passkey")
        println("  unlock     open .env.lokot with an enrolled passkey and write the service files")
        println("  lock       delete the files 'unlock' wrote")
        println("  edit       change the values in .env.lokot, in a full-screen editor")
        println("  add-key    enrol another passkey, so losing one does not lose the vault")
        println("  rekey      re-key the vault, keeping only the passkeys you present")
        println("  selftest   check the crypto and file format against known answers")
        println()
        println("  <target>   a directory, or user@host:/path for one on another machine")
        println("  --rp <id>  init, add-key and rekey: the origin a browser asserts for")
        println("  -i         unlock: also write what only a first run needs")
        println()
        if (Platform.isDebugBinary)
            println("  --trace    log every CTAP exchange. WARNING: the log includes key material.")
        2
    }
}
