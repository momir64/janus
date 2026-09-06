package rs.moma.janus.lokot

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
