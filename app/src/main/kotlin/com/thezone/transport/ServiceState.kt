package com.thezone.transport

import android.content.Context

/**
 * One boolean, persisted: "the broadcast service was deliberately running last
 * time we knew." Set true the instant [BleForegroundService] starts, false the
 * instant it is torn down (deliberate stop, or the rare case Android calls
 * onDestroy before killing it).
 *
 * [BootReceiver] reads this after a reboot to decide whether to bring the
 * service back on its own — a citizen phone left broadcasting must not go
 * silent forever just because the OS restarted, but a phone the user switched
 * off deliberately must stay off.
 */
object ServiceState {
    private const val PREFS = "zone_service_state"
    private const val KEY_ACTIVE = "active"

    fun setActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ACTIVE, active).apply()
    }

    fun wasActive(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACTIVE, false)
}
