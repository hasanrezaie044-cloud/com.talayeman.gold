package com.talayeman.gold.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Thin wrapper around AndroidX BiometricPrompt.
 * The app never sees or stores fingerprint data; Android only reports success / failure.
 */
object BiometricHelper {

    /** Fingerprint / face / any enrolled biometric (class 2 "weak" also covers class 3 "strong"). */
    private const val BIOMETRIC_ONLY = BIOMETRIC_WEAK
    private const val BIOMETRIC_OR_DEVICE_LOCK = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    sealed interface AuthError {
        /** User dismissed the prompt or tapped "use password". Not an error to show. */
        object Cancelled : AuthError
        /** Too many wrong fingerprints; biometrics are blocked for a while. */
        object LockedOut : AuthError
        data class Other(val message: String) : AuthError
    }

    fun canAuthenticate(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_ONLY) == BiometricManager.BIOMETRIC_SUCCESS

    /** Human-readable reason why biometrics can't be used (for the Settings screen). */
    fun unavailableReason(context: Context): String =
        when (BiometricManager.from(context).canAuthenticate(BIOMETRIC_ONLY)) {
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "هیچ اثر انگشتی روی این دستگاه ثبت نشده است. ابتدا در تنظیمات گوشی اثر انگشت اضافه کنید."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "این دستگاه حسگر اثر انگشت ندارد."
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "حسگر اثر انگشت در حال حاضر در دسترس نیست. کمی بعد دوباره تلاش کنید."
            else -> "احراز هویت زیستی در این دستگاه در دسترس نیست."
        }

    /** True if the phone itself has a screen lock (PIN / pattern / password) we can use for recovery. */
    fun canUseDeviceCredential(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(BIOMETRIC_OR_DEVICE_LOCK) == BiometricManager.BIOMETRIC_SUCCESS

    /** Fingerprint prompt with a "use password" button (the app password is the fallback). */
    fun authenticate(
        activity: FragmentActivity,
        title: String = "ورود به طلای من",
        subtitle: String = "اثر انگشت خود را روی حسگر قرار دهید",
        negativeText: String = "استفاده از رمز عبور",
        onSuccess: () -> Unit,
        onError: (AuthError) -> Unit
    ) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText(negativeText)
            .setAllowedAuthenticators(BIOMETRIC_ONLY)
            .setConfirmationRequired(false)
            .build()
        show(activity, info, onSuccess, onError)
    }

    /** Phone screen-lock (or fingerprint) prompt; used to recover from a forgotten app password. */
    fun authenticateWithDeviceLock(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (AuthError) -> Unit
    ) {
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_OR_DEVICE_LOCK)
            .build()
        show(activity, info, onSuccess, onError)
    }

    private fun show(
        activity: FragmentActivity,
        info: BiometricPrompt.PromptInfo,
        onSuccess: () -> Unit,
        onError: (AuthError) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onError(
                        when (errorCode) {
                            BiometricPrompt.ERROR_USER_CANCELED,
                            BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                            BiometricPrompt.ERROR_CANCELED -> AuthError.Cancelled
                            BiometricPrompt.ERROR_LOCKOUT,
                            BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> AuthError.LockedOut
                            else -> AuthError.Other(errString.toString())
                        }
                    )
                }
                // onAuthenticationFailed (a single non-matching finger) is handled by the prompt itself:
                // it shows "not recognized" and lets the user retry, so we must not close the flow.
            }
        )
        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            onError(AuthError.Other(e.message ?: "خطا در نمایش احراز هویت"))
        }
    }
}
