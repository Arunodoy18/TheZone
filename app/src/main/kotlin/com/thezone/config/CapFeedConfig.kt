package com.thezone.config

import android.content.Context

/**
 * Opt-in, user-configured URL for [com.thezone.notify.CapFeedFetcher] — off
 * and empty by default. Deliberately not hardcoded to any specific agency's
 * feed: a real deployment points this at whatever official CAP source is
 * relevant for that incident (NDMA/SACHET, a state SDMA, or a self-hosted
 * relay), the same way a real EOC would configure its own data sources
 * rather than the app assuming one on their behalf.
 */
object CapFeedConfig {

    private const val PREFS = "thezone_cap_feed"
    private const val K_URL = "url"
    private const val K_ENABLED = "enabled"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun feedUrl(context: Context): String = prefs(context).getString(K_URL, "").orEmpty()

    fun setFeedUrl(context: Context, url: String) {
        prefs(context).edit().putString(K_URL, url.trim()).apply()
    }

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(K_ENABLED, false)

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putBoolean(K_ENABLED, on).apply()
    }
}
