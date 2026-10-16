package com.talayeman.gold.util

import android.content.Context

/**
 * Remembers that the user accepted the Terms of Use, so it is asked only once.
 * Stored in a small private SharedPreferences file and read synchronously at start-up, so no app
 * content (and no network / background work) starts before acceptance. Bump [VERSION] if the text
 * ever changes and users must accept it again.
 */
object TermsManager {
    const val VERSION = 1
    private const val PREFS = "talaye_man_terms"
    private const val KEY_ACCEPTED_VERSION = "accepted_version"
    private const val KEY_ACCEPTED_AT = "accepted_at"

    fun isAccepted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_ACCEPTED_VERSION, 0) >= VERSION

    fun accept(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_ACCEPTED_VERSION, VERSION)
            .putLong(KEY_ACCEPTED_AT, System.currentTimeMillis())
            .apply()
    }
}
