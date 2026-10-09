package com.thezone.transport

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * A phone that was broadcasting when it lost power (dead battery, forced
 * reboot, an OEM "optimiser" that restarts the device) must start broadcasting
 * again on its own — nobody is going to unlock a buried or unconscious
 * person's phone and reopen the app for them.
 *
 * [ServiceState] records whether the service was deliberately running; permissions
 * are re-checked because Android never re-grants anything across this receiver.
 * Reference: CLAUDE.md rule 6 (Dead Man's Packet) is worthless if the phone
 * that goes quiet does so because nobody restarted it after a reboot.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!ServiceState.wasActive(context)) return
        val missing = missingPermissions(context)
        if (missing.isNotEmpty()) {
            // No WorkManager / periodic retry here (no new dependency) — but this
            // is exactly the "someone unconscious, phone reboots" scenario CLAUDE.md
            // rule 6 cares about, so at least leave a trail for why broadcasting
            // didn't resume, instead of failing in total silence.
            Log.w("TheZone", "BootReceiver: was active before reboot but missing permissions $missing — not restarting")
            return
        }
        BleForegroundService.start(context)
    }

    private fun missingPermissions(context: Context): List<String> =
        requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    private fun requiredPermissions(): List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_ADVERTISE)
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
