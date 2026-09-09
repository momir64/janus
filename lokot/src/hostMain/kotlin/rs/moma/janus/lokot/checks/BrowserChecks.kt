package rs.moma.janus.lokot.checks

import rs.moma.janus.lokot.browser.parseUnlockBody
import rs.moma.janus.lokot.browser.LoopbackServer
import rs.moma.janus.lokot.browser.challengeJson
import rs.moma.janus.lokot.io.connectLoopback
import rs.moma.janus.lokot.externals.fromHex
import rs.moma.janus.lokot.io.receiveBytes
import rs.moma.janus.lokot.io.closeSocket
import rs.moma.janus.lokot.io.sendBytes

internal fun browserChecks(): List<Check> {
    val group = CheckGroup(BROWSER_BUG)
    val wire = group.section("unlock page wire format")
    val server = group.section("loopback server")

    val credentialId = "5ace47494d2052c2f51673e15ae3b477".fromHex()
    val output = "0b".repeat(32).fromHex()

    return listOf(
        wire.equals(
            "challenge names the relying party", true
        ) { """"rpId":"lokot.localhost"""" in challengeJson("lokot.localhost", ByteArray(32), listOf(credentialId)) },

        wire.equals("challenge encodes ids as base64url", true) {
            "\"Ws5HSU0gUsL1FnPhWuO0dw\"" in challengeJson("lokot.localhost", ByteArray(32), listOf(credentialId))
        },

        wire.equals("a body without an output is refused", null) {
            parseUnlockBody("""{"credentialId":"Ws5HSU0gUsL1FnPhWuO0dw"}""")
        },

        wire.equals("a body that is not base64url is refused", null) {
            parseUnlockBody("""{"credentialId":"not base64!","output":"also not"}""")
        },

        server.equals("serves a GET it is asked for", "GET /challenge") {
            exchange("GET /challenge HTTP/1.1\r\nHost: lokot.localhost\r\n\r\n") { request ->
                request.respond(200, "application/json", "{}")
                "${request.method} ${request.path}"
            }
        },

        server.equals("reads a POST body of the declared length", """{"credentialId":"x"}""") {
            val body = """{"credentialId":"x"}"""
            exchange(
                "POST /unlock HTTP/1.1\r\nHost: lokot.localhost\r\nContent-Length: ${body.length}\r\n\r\n$body"
            ) { request ->
                request.respond(204, null)
                request.body
            }
        },

        server.equals("drops the query string from the path", "/unlock") {
            exchange("GET /unlock?stray=1 HTTP/1.1\r\nHost: lokot.localhost\r\n\r\n") { request ->
                request.respond(404, "text/plain", "no")
                request.path
            }
        },

        server.equals("answers with the status and body it was given", true) {
            var answer = ""
            exchange("GET / HTTP/1.1\r\nHost: lokot.localhost\r\n\r\n", { answer = it }) { request ->
                request.respond(200, "text/html", "<!doctype html>")
                ""
            }
            "200 OK" in answer && "Content-Length: 15" in answer && answer.endsWith("<!doctype html>")
        },
    )
}

private fun <T> exchange(
    request: String,
    onAnswer: (String) -> Unit = {},
    handle: (rs.moma.janus.lokot.browser.HttpRequest) -> T,
): T? {
    val server = LoopbackServer.open() ?: return null
    val client = connectLoopback(server.port)
    if (client < 0) {
        server.close()
        return null
    }

    return try {
        val bytes = request.encodeToByteArray()
        var sent = 0
        while (sent < bytes.size) {
            val wrote = sendBytes(client, bytes, sent)
            if (wrote <= 0) break
            sent += wrote
        }

        val received = server.next() ?: return null
        val answer = handle(received)

        val buffer = ByteArray(4096)
        val got = receiveBytes(client, buffer)
        onAnswer(if (got > 0) buffer.decodeToString(0, got) else "")
        answer
    } finally {
        closeSocket(client)
        server.close()
    }
}
