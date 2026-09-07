package com.thezone.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.thezone.probe.R

/**
 * "Turn Bluetooth on" reminder. Zone is only useful while the radio is on, so
 * when Bluetooth is off we raise a loud, DND-bypassing notification (its own
 * sound + vibration) and clear it the moment Bluetooth comes back.
 *
 * The foreground service registers a runtime receiver for ACTION_STATE_CHANGED
 * and calls [check] / [clear]; [check] is also called on app launch.
 */
object BluetoothNudge {

    private const val CHANNEL = "zone_bluetooth"
    private const val ID = 2001

    fun channel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(
            CHANNEL, "Bluetooth reminder", NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Reminds you to switch Bluetooth on so Zone can keep you connected."
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 150, 250)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            setSound(
                Uri.parse("android.resource://${context.packageName}/${R.raw.zone_bt}"),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        mgr.createNotificationChannel(ch)
    }

    fun isBluetoothOn(context: Context): Boolean {
        val adapter = context.getSystemService(BluetoothAdapter::class.java) ?: return false
        return adapter.isEnabled
    }

    /** Show the reminder iff Bluetooth is currently off; otherwise clear it. */
    fun check(context: Context) {
        if (isBluetoothOn(context)) { clear(context); return }
        channel(context)

        val enable = PendingIntent.getActivity(
            context, 0,
            Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val settings = PendingIntent.getActivity(
            context, 1,
            Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentTitle("Bluetooth is OFF — you are not being heard")
            .setContentText("Zone needs Bluetooth on to stay connected to nearby phones. Tap to turn it on.")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Zone keeps working with no network, but only while Bluetooth is on. " +
                        "Turn it on so your phone can carry and relay signals for people around you.",
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(enable)
            .addAction(0, "Turn on Bluetooth", enable)
            .addAction(0, "Bluetooth settings", settings)
            .setOngoing(true)
            .setAutoCancel(false)
            .build()

        context.getSystemService(NotificationManager::class.java).notify(ID, n)
    }

    fun clear(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID)
    }
}
