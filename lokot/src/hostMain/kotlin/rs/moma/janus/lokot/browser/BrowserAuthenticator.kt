package rs.moma.janus.lokot.browser

import rs.moma.janus.lokot.externals.Authenticator
import rs.moma.janus.lokot.externals.HmacSecret
import rs.moma.janus.lokot.io.browserCommand
import rs.moma.janus.lokot.externals.Crypto
import rs.moma.janus.lokot.io.startProcess

internal const val BROWSER_OPTION = "browser"

internal fun unlockViaBrowser(project: String, salt: ByteArray, credentialIds: List<ByteArray>): HmacSecret? {
    if (credentialIds.isEmpty()) {
        println("No key is enrolled for '${Authenticator.RP_ID}', so a browser has nothing to sign with.")
        return null
    }
    return ceremony(
        "Sign in with a passkey for ${Authenticator.RP_ID}. A phone works: pick the QR code option.",
        challengeJson(Authenticator.RP_ID, salt, credentialIds).with(
            """"cli":true""",
            """"label":"kalauz for $project"""",
            """"action":"UNLOCK"""",
        ),
    )
}

internal fun enrolViaBrowser(project: String, salt: ByteArray, enrolled: List<ByteArray>): HmacSecret? = ceremony(
    "Create a passkey for ${Authenticator.RP_ID}. A phone works: pick the QR code option.",
    enrolChallengeJson(project, salt, enrolled),
)

private fun ceremony(instruction: String, challenge: String): HmacSecret? {
    val server = LoopbackServer.open() ?: run {
        println("Could not open a loopback port for the browser.")
        return null
    }

    try {
        val address = "http://${Authenticator.RP_ID}:${server.port}/"
        println()
        println(instruction)
        println("Opening $address")
        println("If no browser comes up, go to that address yourself.")
        openBrowser(address)
        println("Waiting for the browser. Ctrl+C gives up.")

        while (true) {
            val request = server.next() ?: run {
                println("The browser stopped talking before anything was signed.")
                return null
            }
            when {
                request.method == "GET" && request.path == "/challenge" ->
                    request.respond(200, "application/json", challenge)

                request.method == "POST" && request.path == "/unlock" -> {
                    val answer = parseUnlockBody(request.body)
                    if (answer == null) {
                        request.respond(400, "text/plain", "malformed")
                    } else {
                        request.respond(204, null)
                        return HmacSecret(answer.first, answer.second)
                    }
                }

                request.method == "GET" && request.path == "/" ->
                    request.respond(200, "text/html; charset=utf-8", UNLOCK_PAGE)

                else -> request.respond(404, "text/plain", "not found")
            }
        }
    } finally {
        server.close()
    }
}

private fun userId(project: String) = Crypto.sha256(project.encodeToByteArray()).copyOf(16)

private fun String.with(vararg fields: String) = removeSuffix("}") + fields.joinToString("") { ",$it" } + "}"

private fun enrolChallengeJson(project: String, salt: ByteArray, enrolled: List<ByteArray>) =
    challengeJson(Authenticator.RP_ID, salt, enrolled).with(
        """"cli":true""",
        """"enrol":true""",
        """"userId":"${userId(project).toUrlBase64()}"""",
        """"label":"kalauz for $project"""",
        """"action":"ADD KEY"""",
    )

private fun openBrowser(address: String) {
    try {
        startProcess(browserCommand(address))?.close()
    } catch (_: Exception) {
    }
}
