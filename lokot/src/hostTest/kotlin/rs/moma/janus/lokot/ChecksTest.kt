package rs.moma.janus.lokot

import kotlin.test.Test
import kotlin.test.fail

class ChecksTest {
    @Test
    fun allChecksPass() {
        val failures = allChecks().mapNotNull { check ->
            check.run()?.let { "${check.section} / ${check.name}: $it" }
        }
        if (failures.isNotEmpty()) fail(failures.joinToString("\n", prefix = "\n"))
    }
}
