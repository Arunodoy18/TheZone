package com.thezone.mode

import android.content.Context

/** One APK, three roles (PRD §3). Chosen on first launch, then sticky. */
enum class AppMode(val label: String, val blurb: String) {
    CITIZEN("Citizen", "You need to be found. Broadcasts even if the phone is buried."),
    RESPONDER("Responder", "You are searching. A triage-sorted list and a Dig Here bar."),
    MAP("Map", "The room screen. Severity grid, and where the network went dark."),
}

object ModeStore {

    private const val PREFS = "thezone_mode"
    private const val KEY = "app_mode"

    fun get(context: Context): AppMode? =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null)
            ?.let { name -> AppMode.entries.firstOrNull { it.name == name } }

    fun set(context: Context, mode: AppMode) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, mode.name).apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
