package com.talayeman.gold.util

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * App-lock credentials.
 *
 * - The 4-digit password is NEVER stored: only a salted PBKDF2-HMAC-SHA256 hash (random 16-byte
 *   salt, 120 000 iterations) inside EncryptedSharedPreferences (Android Keystore backed).
 * - Wrong attempts are counted and persisted; after every 5 consecutive failures the lock screen
 *   is blocked for a growing delay (30 s, 60 s, 2 min ... max 15 min), which makes brute-forcing a
 *   4-digit code impractical.
 * - "Lock enabled" == a password exists. Fingerprint is an optional shortcut on top of it and is
 *   only valid while a password exists (the password is always the fallback).
 * - The app never reads or stores fingerprint data; Android's BiometricPrompt only reports success.
 */
class PinManager private constructor(private val appContext: Context) {

    sealed interface VerifyResult {
        object Success : VerifyResult
        data class Wrong(val attemptsLeftInRound: Int) : VerifyResult
        data class Locked(val secondsLeft: Long) : VerifyResult
        object Invalid : VerifyResult
    }

    private val prefs: SharedPreferences by lazy {
        try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "talaye_man_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Keystore problems exist on some devices/ROMs. The stored value is a salted hash anyway,
            // so fall back to app-private preferences instead of making the app unusable.
            appContext.getSharedPreferences("talaye_man_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    fun hasPin(): Boolean = prefs.contains(KEY_HASH) && prefs.contains(KEY_SALT)

    fun isValidPin(pin: String): Boolean = pin.length == PIN_LENGTH && pin.all { it in '0'..'9' }

    fun setPin(pin: String): Boolean {
        if (!isValidPin(pin)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        prefs.edit()
            .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString(KEY_HASH, Base64.encodeToString(hash(pin, salt), Base64.NO_WRAP))
            .putInt(KEY_FAILS, 0)
            .putLong(KEY_LOCKED_UNTIL, 0L)
            .apply()
        return true
    }

    fun verifyPin(pin: String): VerifyResult {
        val remaining = lockSecondsLeft()
        if (remaining > 0) return VerifyResult.Locked(remaining)
        if (!isValidPin(pin)) return VerifyResult.Invalid
        val salt = prefs.getString(KEY_SALT, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        val stored = prefs.getString(KEY_HASH, null)?.let { Base64.decode(it, Base64.NO_WRAP) }
        if (salt == null || stored == null) return VerifyResult.Invalid

        return if (MessageDigest.isEqual(stored, hash(pin, salt))) {
            prefs.edit().putInt(KEY_FAILS, 0).putLong(KEY_LOCKED_UNTIL, 0L).apply()
            VerifyResult.Success
        } else {
            val fails = prefs.getInt(KEY_FAILS, 0) + 1
            val editor = prefs.edit().putInt(KEY_FAILS, fails)
            if (fails % MAX_ATTEMPTS == 0) {
                val round = fails / MAX_ATTEMPTS // 1, 2, 3 ...
                val seconds = minOf(MAX_LOCK_SECONDS, BASE_LOCK_SECONDS shl minOf(round - 1, 10))
                editor.putLong(KEY_LOCKED_UNTIL, System.currentTimeMillis() + seconds * 1000)
                editor.apply()
                VerifyResult.Locked(seconds)
            } else {
                editor.apply()
                VerifyResult.Wrong(MAX_ATTEMPTS - fails % MAX_ATTEMPTS)
            }
        }
    }

    /** Seconds the lock screen is still blocked after too many wrong attempts (0 = not blocked). */
    fun lockSecondsLeft(): Long {
        val until = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        val left = (until - System.currentTimeMillis() + 999) / 1000
        return if (left > 0) left else 0L
    }

    /** Removes the password and everything that depends on it (fingerprint shortcut, counters). */
    fun clearPin() {
        prefs.edit()
            .remove(KEY_HASH).remove(KEY_SALT)
            .remove(KEY_FAILS).remove(KEY_LOCKED_UNTIL)
            .remove(KEY_BIOMETRIC)
            .apply()
    }

    fun isBiometricEnabled(): Boolean = hasPin() && prefs.getBoolean(KEY_BIOMETRIC, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC, enabled && hasPin()).apply()
    }

    private fun hash(pin: String, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    companion object {
        const val PIN_LENGTH = 4
        private const val MAX_ATTEMPTS = 5
        private const val BASE_LOCK_SECONDS = 30L
        private const val MAX_LOCK_SECONDS = 15 * 60L
        private const val ITERATIONS = 120_000

        private const val KEY_HASH = "app_pin_hash_v2"
        private const val KEY_SALT = "app_pin_salt_v2"
        private const val KEY_FAILS = "app_pin_fails"
        private const val KEY_LOCKED_UNTIL = "app_pin_locked_until"
        private const val KEY_BIOMETRIC = "app_biometric_enabled"

        @Volatile
        private var instance: PinManager? = null

        fun get(context: Context): PinManager =
            instance ?: synchronized(this) {
                instance ?: PinManager(context.applicationContext).also { instance = it }
            }
    }
}
