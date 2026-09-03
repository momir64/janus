package rs.moma.privezak.security

import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import kotlinx.coroutines.suspendCancellableCoroutine
import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricPrompt
import kotlin.coroutines.resume
import javax.crypto.Cipher

sealed interface AuthResult {
    data class Success(val cipher: Cipher) : AuthResult
    data class Failed(val message: String) : AuthResult
    data object Cancelled : AuthResult
}

private val DISMISSED = setOf(
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_CANCELED
)

suspend fun FragmentActivity.authenticate(cipher: Cipher, title: String): AuthResult =
    suspendCancellableCoroutine { continuation ->
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                val released = result.cryptoObject?.cipher
                if (continuation.isActive)
                    continuation.resume(released?.let(AuthResult::Success) ?: AuthResult.Cancelled)
            }

            override fun onAuthenticationError(code: Int, message: CharSequence) {
                if (continuation.isActive) {
                    continuation.resume(
                        if (code in DISMISSED) AuthResult.Cancelled
                        else AuthResult.Failed(message.toString())
                    )
                }
            }
        }

        val prompt = BiometricPrompt(this, mainExecutor, callback)
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BIOMETRIC_STRONG)
                .build(),
            BiometricPrompt.CryptoObject(cipher)
        )
        continuation.invokeOnCancellation { prompt.cancelAuthentication() }
    }
