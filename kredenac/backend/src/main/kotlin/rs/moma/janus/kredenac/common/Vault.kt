package rs.moma.janus.kredenac.common

import rs.moma.janus.lokot.Lokot
import kotlin.io.path.exists
import kotlin.io.path.Path

// Beside the compose-file in a deployment, one directory up when the backend is run from the IDE.
private val VAULT_FILE = Path(".env.lokot").takeIf { it.exists() } ?: Path("..", ".env.lokot")
val vault: Lokot by lazy { Lokot(VAULT_FILE) }

fun Lokot.text(name: String): String {
    val chars = get(name)
    return String(chars).also { chars.fill('\u0000') }
}
