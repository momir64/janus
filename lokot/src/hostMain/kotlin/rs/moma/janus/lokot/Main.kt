package rs.moma.janus.lokot

import kotlin.system.exitProcess
import kotlin.native.Platform

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
fun main(args: Array<String>) {
    Authenticator.initialise(trace = args.contains("--trace"))

    when (args.firstOrNull()) {
        "init" -> exitProcess(runInit())
        "selftest" -> exitProcess(runSelftest())
        else -> {
            println("usage: lokot <command> [options]\n")
            println("  init       create .env.lokot from lokot.toml, enrolling a passkey")
            println("  selftest   check the crypto and file format against known answers\n")
            if (Platform.isDebugBinary)
                println("  --trace    log every CTAP exchange. WARNING: the log includes key material.\n")
            exitProcess(2)
        }
    }
}
