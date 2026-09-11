package com.thezone.notify

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.thezone.config.EmergencyContacts
import com.thezone.demo.UserStatus
import com.thezone.packet.Status
import com.thezone.sensors.Position

/**
 * Opportunistic "I'm okay" SMS to your own emergency contacts — a completely
 * different reach model from the BLE mesh: not a broadcast to strangers
 * nearby, just your status reaching the few people who already have your
 * number, the moment the phone catches any signal at all. SMS rides the
 * voice/control channel, not data, so it can go through on far weaker signal
 * than the internet needs.
 *
 * Opt-in only, numbers hand-typed by the user ([EmergencyContacts]) — this is
 * the one place in the app that touches a live network (CLAUDE.md rule 4),
 * so it stays off until the user explicitly turns it on.
 *
 * Retries on failure via the same relay pump that drives everything else
 * (TransportController.pumpOnce), rate-limited so a dead radio doesn't get
 * hammered every 2s tick.
 */
object StatusTexter {

    private const val TAG = "TheZone"
    private const val ACTION_SENT = "com.thezone.SMS_SENT"

    /** Don't re-text the same status once it's gone through. */
    private const val COOLDOWN_MS = 30L * 60_000

    /** Don't retry a failed/no-signal send more than once a minute. */
    private const val RETRY_INTERVAL_MS = 60_000L

    @Volatile private var receiverRegistered = false
    @Volatile private var lastSentStatusCode: Int? = null
    @Volatile private var lastSentAtMillis = 0L
    @Volatile private var lastAttemptAtMillis = 0L
    @Volatile private var requestSeq = 0L

    /** One-line human status for the settings screen. */
    @Volatile var lastResult: String = "not tried yet"
        private set

    /** Called every pump tick; cheap and rate-limited internally when disabled or waiting. */
    fun maybeAttempt(context: Context) {
        val app = context.applicationContext
        if (!EmergencyContacts.enabled(app)) { lastResult = "off"; return }
        val numbers = EmergencyContacts.numbers(app)
        if (numbers.isEmpty()) { lastResult = "no contacts saved"; return }
        if (!hasPermission(app)) { lastResult = "needs SMS permission"; return }

        val now = System.currentTimeMillis()
        val statusCode = currentStatusCode()
        if (statusCode == lastSentStatusCode && now - lastSentAtMillis < COOLDOWN_MS) {
            lastResult = "sent — up to date"
            return
        }
        if (now - lastAttemptAtMillis < RETRY_INTERVAL_MS) return
        lastAttemptAtMillis = now
        send(app, numbers, statusCode)
    }

    /** Manual "send now" from the settings screen — bypasses the cooldown, not the permission/numbers check. */
    fun sendNow(context: Context) {
        val app = context.applicationContext
        val numbers = EmergencyContacts.numbers(app)
        if (numbers.isEmpty()) { lastResult = "no contacts saved"; return }
        if (!hasPermission(app)) { lastResult = "needs SMS permission"; return }
        lastAttemptAtMillis = System.currentTimeMillis()
        send(app, numbers, currentStatusCode())
    }

    private fun send(context: Context, numbers: List<String>, statusCode: Int) {
        ensureReceiver(context)
        val message = messageFor(statusCode)
        val sms = smsManager(context)
        if (sms == null) { lastResult = "no SmsManager on this device"; return }
        val myRequest = ++requestSeq
        lastResult = "sending…"
        numbers.forEach { number ->
            runCatching {
                val sent = PendingIntent.getBroadcast(
                    context, myRequest.toInt(),
                    Intent(ACTION_SENT).setPackage(context.packageName).putExtra("req", myRequest),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                sms.sendTextMessage(number, null, message, sent, null)
            }.onFailure { Log.w(TAG, "STATUS_SMS send failed for $number: ${it.message}") }
        }
        Log.w(TAG, "STATUS_SMS attempt req=$myRequest to=${numbers.size} status=$statusCode")
    }

    private fun ensureReceiver(context: Context) {
        if (receiverRegistered) return
        receiverRegistered = true
        val rx = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                val req = i.getLongExtra("req", -1)
                if (req != requestSeq) return   // a stale/superseded attempt
                if (resultCode == Activity.RESULT_OK) {
                    lastSentStatusCode = currentStatusCode()
                    lastSentAtMillis = System.currentTimeMillis()
                    lastResult = "sent"
                    Log.w(TAG, "STATUS_SMS delivered req=$req")
                } else {
                    lastResult = "failed (code $resultCode) — will retry"
                    Log.w(TAG, "STATUS_SMS failed req=$req code=$resultCode")
                }
            }
        }
        ContextCompat.registerReceiver(
            context.applicationContext, rx, IntentFilter(ACTION_SENT),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun messageFor(statusCode: Int): String {
        val label = when (Status.fromCode(statusCode)) {
            Status.SAFE -> "I'm safe"
            Status.TRAPPED_DEBRIS -> "I may be trapped"
            Status.RISING_WATER -> "Water is rising near me"
            Status.INJURED -> "I'm injured"
            Status.HAVE_RESOURCE -> "I have supplies, I'm okay"
            Status.RESPONDER -> "I'm responding, I'm okay"
            else -> "Checking in"
        }
        val fix = Position.snapshot()
        val where = if (fix != null && fix.ageMillis() <= 10L * 60 * 1000) {
            "near %.4f, %.4f".format(fix.lat, fix.lon)
        } else {
            "location unknown"
        }
        return "Zone: $label. $where. Sent automatically when signal returned."
    }

    private fun currentStatusCode(): Int = UserStatus.code ?: Status.UNKNOWN.code

    private fun smsManager(context: Context): SmsManager? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
    }.getOrNull()

    private fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) ==
            PackageManager.PERMISSION_GRANTED
}
