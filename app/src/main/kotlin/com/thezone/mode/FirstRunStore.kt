package com.thezone.mode

import android.content.Context

/**
 * One-time, persisted "have we already nudged this install" flags — separate
 * from [ModeStore] since this isn't about which mode is active, just whether a
 * particular first-run prompt has already been shown once.
 */
object FirstRunStore {

    private const val PREFS = "thezone_firstrun"
    private const val KEY_BATTERY_PROMPT_SEEN = "battery_prompt_seen"

    /**
     * "Keep Zone alive" (battery exemption) used to be reachable only through an
     * undiscoverable long-press gesture — a real user who never found it would
     * have BLE start correctly, then get silently killed in the background by
     * Doze/an OEM battery manager with no idea why. This flag gates a one-time,
     * skippable nudge shown right after the first permission grant instead.
     */
    fun batteryPromptSeen(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_BATTERY_PROMPT_SEEN, false)

    fun markBatteryPromptSeen(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_BATTERY_PROMPT_SEEN, true).apply()
    }
}
