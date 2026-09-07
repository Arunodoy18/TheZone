package com.thezone.transport

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.thezone.notify.BluetoothNudge

/**
 * Keeps the radio alive when the app is not foregrounded (CLAUDE.md "use a
 * foreground service ... or Android will kill the advertiser").
 *
 * It does not own the transport — [TransportController] does. The service just
 * pins the process with a persistent notification and asks the controller to
 * start on the BLE transport, and to stop when it is torn down.
 */
class BleForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    /** Fires the "turn Bluetooth on" reminder the instant the adapter is switched off. */
    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, i: Intent) {
            when (i.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_OFF, BluetoothAdapter.STATE_TURNING_OFF -> {
                    BluetoothNudge.check(applicationContext)
                    postForeground(btOn = false)
                }
                BluetoothAdapter.STATE_ON -> {
                    BluetoothNudge.clear(applicationContext)
                    postForeground(btOn = true)
                }
            }
        }
    }
    private var btReceiverRegistered = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        if (!btReceiverRegistered) {
            ContextCompat.registerReceiver(
                this, btReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            btReceiverRegistered = true
        }
        BluetoothNudge.check(applicationContext)
        // Keep whatever transport is active alive across a screen lock — including
        // Simulated / File, so the "BLE won't link, switch to the simulator"
        // failure drill survives the phone locking mid-demo.
        if (TransportController.kind == "none") {
            TransportController.useBle(applicationContext)
        }
        TransportController.start(applicationContext)
        return START_STICKY
    }

    override fun onDestroy() {
        if (btReceiverRegistered) runCatching { unregisterReceiver(btReceiver) }
        TransportController.stop()
        super.onDestroy()
    }

    /** Re-post the persistent notification with text that reflects the Bluetooth state. */
    private fun postForeground(btOn: Boolean) {
        val n = buildForegroundNotification(btOn)
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, n)
    }

    private fun buildForegroundNotification(btOn: Boolean): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (btOn) "You are being heard" else "Bluetooth OFF — not connected")
            .setContentText(
                if (btOn) "Broadcasting and relaying nearby signals."
                else "Turn Bluetooth on so Zone can keep you connected.",
            )
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun startForegroundCompat() {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Signal broadcast",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply { description = "Keeps your phone broadcasting and listening for nearby devices." },
            )
        }
        val notification: Notification =
            buildForegroundNotification(btOn = BluetoothNudge.isBluetoothOn(this))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "thezone_broadcast"
        private const val NOTIFICATION_ID = 1001

        fun start(context: Context) {
            val intent = Intent(context, BleForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BleForegroundService::class.java))
        }
    }
}
