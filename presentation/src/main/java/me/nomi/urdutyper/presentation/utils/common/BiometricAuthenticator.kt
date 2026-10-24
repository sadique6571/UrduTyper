package me.nomi.urdutyper.presentation.utils.common

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricAuthenticator(
    private val context: Context,
    private val title: String,
    private val subtitle: String,
    private val negativeButtonText: String,
    private val onAuthenticationSucceeded: (() -> Unit)? = null,
    private val onAuthenticationError: ((errorCode: Int, errString: CharSequence) -> Unit)? = null,
    private val onAuthenticationFailed: (() -> Unit)? = null,
    private val onBiometricUnavailable: (() -> Unit)? = null
) {
    fun authenticate() {
        val biometricManager = BiometricManager.from(context)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                // Biometric authentication is available
                val executor = ContextCompat.getMainExecutor(context)
                val biometricPrompt = BiometricPrompt(context as FragmentActivity, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(
                            errorCode: Int,
                            errString: CharSequence
                        ) {
                            super.onAuthenticationError(errorCode, errString)
                            onAuthenticationError?.invoke(errorCode, errString)
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            onAuthenticationSucceeded?.invoke()
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            onAuthenticationFailed?.invoke()
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(title)
                    .setSubtitle(subtitle)
                    .setNegativeButtonText(negativeButtonText)
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }

            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN -> {
                onBiometricUnavailable?.invoke()
            }
        }
    }
}