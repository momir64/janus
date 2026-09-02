package rs.moma.janus.kredenac

import rs.moma.janus.kredenac.common.clientInfo
import kotlin.test.assertEquals
import kotlin.test.Test

class ClientInfoTest {
    private fun check(agent: String, browser: String?, device: String?) {
        val info = clientInfo(agent, null, null, null)
        assertEquals(browser, info.browser, "browser for: $agent")
        assertEquals(device, info.device, "device for: $agent")
    }

    @Test
    fun identifiesCommonClients() {
        check(
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/152.0 Safari/537.36",
            "Chrome", "Windows"
        )
        check(
            "Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/152.0 Safari/537.36 Edg/152.0",
            "Microsoft Edge", "Windows"
        )
        check("Mozilla/5.0 (iPhone; CPU iPhone OS 18_7 like Mac OS X) Version/26.6 Safari/604.1", "Safari", "iPhone")
        check("Mozilla/5.0 (iPad; CPU OS 18_7 like Mac OS X) Version/26.6 Safari/604.1", "Safari", "iPad")
        check("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Firefox/154.0", "Firefox", "macOS")
        check("Mozilla/5.0 (X11; CrOS x86_64 14541.0.0) Chrome/152.0 Safari/537.36", "Chrome", "ChromeOS")
        check("Mozilla/5.0 (Linux; Android 14) Chrome/152.0 Mobile Safari/537.36", "Chrome", "Android")
        check("curl/8.4.0", null, null)
        check("", null, null)
    }
}