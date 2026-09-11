package com.thezone.notify

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.thezone.core.AlertRecord
import com.thezone.packet.AlertText

/**
 * Broadcasts a WARNING / EXTREME mesh alert as this phone's Bluetooth device
 * name — anyone nearby who opens Settings > Bluetooth sees "ZONE ALERT: ..."
 * with no pairing and no app installed. A real public API
 * (BluetoothAdapter.setName), unlike the Wi-Fi SSID idea (dropped — that one
 * needs a system-app-only API not open to a third-party app). Passive and
 * low-visibility next to [SirenBeacon]: this is the glance-in-settings
 * channel, not the can't-miss-it one — the two are meant to run together.
 *
 * Restores the phone's real name once the alert's own window closes, or
 * [stop] is called, so the phone doesn't stay renamed after the fact.
 */
object BluetoothNameBeacon {

    private const val TAG = "TheZone"
    private const val MAX_RUN_MS = 10 * 60_000L
    private const val MIN_RUN_MS = 30_000L
    private const val NAME_PREFIX = "ZONE ALERT: "
    private const val NAME_MAX_BYTES = 40

    private val handler = Handler(Looper.getMainLooper())
    private var appContextRef: Context? = null
    private var originalName: String? = null
    private var running = false

    fun start(context: Context, rec: AlertRecord) {
        val app = context.applicationContext
        handler.post { startOnMain(app, rec) }
    }

    /** Restore the phone's real Bluetooth name (independent of SirenBeacon's ack). */
    fun stop() {
        handler.post { stopOnMain() }
    }

    private fun startOnMain(app: Context, rec: AlertRecord) {
        if (running) return
        if (!hasPermission(app)) {
            Log.w(TAG, "BT_NAME_BEACON skipped — Bluetooth permission not granted")
            return
        }
        appContextRef = app
        val adapter = runCatching { app.getSystemService(BluetoothManager::class.java)?.adapter }.getOrNull()
        if (adapter == null) {
            Log.w(TAG, "BT_NAME_BEACON skipped — no adapter")
            return
        }
        val name = nameFor(rec)
        runCatching {
            if (originalName == null) originalName = adapter.name
            adapter.name = name
            running = true
            Log.w(TAG, "BT_NAME_BEACON set name=\"$name\"")
        }.onFailure { Log.w(TAG, "BT_NAME_BEACON failed: ${it.message}") }

        if (!running) return
        val validMs = (rec.expiresAtMillis - rec.issuedAtMillis).coerceAtLeast(0)
        handler.postDelayed({ stopOnMain() }, minOf(validMs, MAX_RUN_MS).coerceAtLeast(MIN_RUN_MS))
    }

    private fun stopOnMain() {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        runCatching {
            val app = appContextRef ?: return@runCatching
            val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return@runCatching
            originalName?.let { adapter.name = it }
        }.onFailure { Log.w(TAG, "BT_NAME_BEACON restore failed: ${it.message}") }
        originalName = null
        Log.w(TAG, "BT_NAME_BEACON stop")
    }

    private fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    private fun nameFor(rec: AlertRecord): String {
        val label = AlertText.label(rec.phraseCode)
        val room = NAME_MAX_BYTES - NAME_PREFIX.toByteArray(Charsets.UTF_8).size
        return NAME_PREFIX + truncateUtf8(label, room)
    }

    /** Byte-safe truncation so a multi-byte char never splits mid-codepoint. */
    private fun truncateUtf8(s: String, maxBytes: Int): String {
        if (s.toByteArray(Charsets.UTF_8).size <= maxBytes) return s
        var end = s.length
        while (end > 0 && s.substring(0, end).toByteArray(Charsets.UTF_8).size > maxBytes) end--
        return s.substring(0, end)
    }
}
