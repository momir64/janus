package rs.moma.janus.lokot

import kotlin.reflect.KFunction2
import kotlin.reflect.KFunction3
import kotlin.reflect.KFunction6
import kotlin.reflect.KFunction
import kotlin.native.Platform
import kotlinx.cinterop.*
import platform.posix.*
import libfido2.*

class HmacSecret(val credentialId: ByteArray, val output: ByteArray)

class Enrolment(val credentialId: ByteArray, output: ByteArray?) {
    val secret = output?.let { HmacSecret(credentialId, it) }
}

@OptIn(ExperimentalForeignApi::class, kotlin.experimental.ExperimentalNativeApi::class)
class Authenticator private constructor(val path: String, private val device: CPointer<fido_dev_t>) {
    val isWindowsHello: Boolean = fido_dev_is_winhello(device)

    context(scope: MemScope)
    val ByteArray.uBytes: CPointer<UByteVar>; get() = this.toUBytes(scope)

    fun enrol(pin: String?, project: String, salt: ByteArray): Enrolment = memScoped {
        val credential = fido_cred_new() ?: error("fido_cred_new returned null")

        try {
            ok(::fido_cred_set_type, credential, COSE_ES256)
            ok(::fido_cred_set_rp, credential, RP_ID, "lokot")
            ok(::fido_cred_set_clientdata, credential, CLIENT_DATA.uBytes, CLIENT_DATA.size())

            val userId = Crypto.sha256(project.encodeToByteArray()).copyOf(16)
            ok(::fido_cred_set_user, credential, userId.uBytes, 16u, project, project, null)

            ok(::fido_cred_set_extensions, credential, FIDO_EXT_HMAC_SECRET or FIDO_EXT_HMAC_SECRET_MC)
            ok(::fido_cred_set_hmac_salt, credential, prfSalt(salt).uBytes, HMAC_OUTPUT_SIZE.convert())

            ok(::fido_cred_set_rk, credential, FIDO_OPT_TRUE)
            ok(::fido_cred_set_uv, credential, FIDO_OPT_TRUE)

            ok(::fido_dev_make_cred, device, credential, pin)

            val pointer = fido_cred_id_ptr(credential) ?: error("no credential id returned")
            Enrolment(
                credentialId = pointer.readBytes(fido_cred_id_len(credential).toInt()),
                output = fido_cred_hmac_secret_ptr(credential)
                    ?.takeIf { fido_cred_hmac_secret_len(credential).toInt() == HMAC_OUTPUT_SIZE }
                    ?.readBytes(HMAC_OUTPUT_SIZE),
            )
        } finally {
            fido_cred_free(cValuesOf(credential))
        }
    }

    /**
     * One UV+UP assertion. [credentialIds] is offered to the authenticator, it picks
     * whichever it holds. That's why every credential in a file shares one salt,
     * since the salt has to be chosen before it is known which key will answer.
     */
    fun hmacSecret(pin: String?, credentialIds: List<ByteArray>, salt: ByteArray): HmacSecret = memScoped {
        require(credentialIds.isNotEmpty()) { "no credentials to try" }
        val assertion = fido_assert_new() ?: error("fido_assert_new returned null")
        try {
            ok(::fido_assert_set_rp, assertion, RP_ID)
            ok(::fido_assert_set_clientdata, assertion, CLIENT_DATA.uBytes, CLIENT_DATA.size())
            credentialIds.forEach { ok(::fido_assert_allow_cred, assertion, it.uBytes, it.size()) }
            ok(::fido_assert_set_extensions, assertion, FIDO_EXT_HMAC_SECRET)
            ok(::fido_assert_set_hmac_salt, assertion, prfSalt(salt).uBytes, HMAC_OUTPUT_SIZE.convert())

            ok(::fido_assert_set_uv, assertion, FIDO_OPT_TRUE)
            ok(::fido_assert_set_up, assertion, FIDO_OPT_TRUE)

            ok(::fido_dev_get_assert, device, assertion, pin)

            val output = fido_assert_hmac_secret_ptr(assertion, 0u) ?: error("no hmac-secret output, prf unsupported")
            val length = fido_assert_hmac_secret_len(assertion, 0u).toInt()
            if (length != HMAC_OUTPUT_SIZE) error("expected $HMAC_OUTPUT_SIZE bytes of hmac-secret output, got $length")
            val answered = fido_assert_id_ptr(assertion, 0u) ?: error("assertion did not report credential id")

            HmacSecret(
                credentialId = answered.readBytes(fido_assert_id_len(assertion, 0u).toInt()),
                output = output.readBytes(HMAC_OUTPUT_SIZE)
            )
        } finally {
            fido_assert_free(cValuesOf(assertion))
        }
    }

    fun close() {
        fido_dev_close(device)
        memScoped { fido_dev_free(cValuesOf(device)) }
    }

    companion object {
        private val PRF_PREFIX = "WebAuthn PRF".encodeToByteArray() + byteArrayOf(0)
        private val CLIENT_DATA = """{"origin":"lokot"}""".encodeToByteArray()
        const val RP_ID = "lokot.localhost"
        const val HMAC_OUTPUT_SIZE = 32

        private const val MAX_DEVICES = 8uL

        fun initialise(trace: Boolean = false) {
            val enabled = trace && Platform.isDebugBinary
            if (trace && !enabled) println("--trace is a debug-build option; this binary ignores it.")
            fido_init(FIDO_DISABLE_U2F_FALLBACK or (if (enabled) FIDO_DEBUG else 0))
            fido_set_log_handler(if (enabled) staticCFunction(::printLogLine) else staticCFunction(::dropLogLine))
        }

        fun paths(): List<String> = memScoped {
            val list = fido_dev_info_new(MAX_DEVICES) ?: error("fido_dev_info_new returned null")
            try {
                val found = alloc<size_tVar>()
                ok(::fido_dev_info_manifest, list, MAX_DEVICES, found.ptr)
                (0 until found.value.toInt()).mapNotNull {
                    fido_dev_info_path(fido_dev_info_ptr(list, it.convert()))?.toKString()
                }
            } finally {
                fido_dev_info_free(cValuesOf(list), MAX_DEVICES)
            }
        }

        fun open(path: String): Authenticator {
            val device = fido_dev_new() ?: error("fido_dev_new returned null")
            ok(::fido_dev_open, device, path)
            return Authenticator(path, device)
        }

        /** SHA-256("WebAuthn PRF" || 0x00 || salt), matching what a browser sends for PRF. */
        private fun prfSalt(salt: ByteArray) = Crypto.sha256(PRF_PREFIX + salt)

        private fun <A, B> ok(fido: KFunction2<A, B, Int>, a: A, b: B) = check(fido, fido(a, b))
        private fun <A, B, C> ok(fido: KFunction3<A, B, C, Int>, a: A, b: B, c: C) = check(fido, fido(a, b, c))
        private fun <A, B, C, D, E, F> ok(fido: KFunction6<A, B, C, D, E, F, Int>, a: A, b: B, c: C, d: D, e: E, f: F) =
            check(fido, fido(a, b, c, d, e, f))

        private fun check(fido: KFunction<Int>, result: Int) {
            if (result != FIDO_OK) error("${fido.name} failed: ${fido_strerr(result)?.toKString() ?: result.toString()}")
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun dropLogLine(@Suppress("UNUSED_PARAMETER") line: CPointer<ByteVar>?) = Unit

@OptIn(ExperimentalForeignApi::class)
private fun printLogLine(line: CPointer<ByteVar>?) {
    line?.toKString()?.let { print(it) }
}
