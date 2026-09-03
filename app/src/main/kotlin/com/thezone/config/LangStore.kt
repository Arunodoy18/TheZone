package com.thezone.config

import android.content.Context

/**
 * UI language override for the Citizen screen — the one screen a local victim
 * actually reads. "system" follows the phone; otherwise a BCP-47 tag.
 *
 * Scoped to Citizen for now (short, high-stakes phrases). Full-app localisation
 * is a `values-xx/` pass; the translations here still want a native-speaker check.
 */
object LangStore {

    private const val PREFS = "thezone_lang"
    private const val KEY = "citizen_lang"

    /** null = follow the system locale. */
    fun tag(context: Context): String? {
        val v = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "system")
        return if (v == null || v == "system") null else v
    }

    fun set(context: Context, tag: String) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, tag).apply()
    }

    /** (tag, label) for the picker. "system" first. */
    val options = listOf(
        "system" to "System",
        "en" to "EN",
        "hi" to "हि",
        "ne" to "ने",
    )
}
