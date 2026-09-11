package com.thezone.config

import android.content.Context

/**
 * User-typed emergency contact phone numbers for [com.thezone.notify.StatusTexter].
 * Always hand-entered — this never reads the phone's own contact list, so the
 * feature needs no contacts permission at all, only SEND_SMS.
 */
object EmergencyContacts {

    private const val PREFS = "thezone_emergency_contacts"
    private const val K_NUMBERS = "numbers"   // newline-separated, as typed
    private const val K_ENABLED = "enabled"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun numbers(context: Context): List<String> =
        rawNumbers(context).lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun rawNumbers(context: Context): String = prefs(context).getString(K_NUMBERS, "").orEmpty()

    fun setNumbers(context: Context, raw: String) {
        prefs(context).edit().putString(K_NUMBERS, raw).apply()
    }

    /** Off by default — this is the one feature in the app that touches a live network. */
    fun enabled(context: Context): Boolean = prefs(context).getBoolean(K_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(K_ENABLED, on).apply()
    }
}
