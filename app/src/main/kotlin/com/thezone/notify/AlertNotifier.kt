package com.thezone.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.thezone.core.AlertRecord
import com.thezone.packet.AlertText
import com.thezone.packet.PacketCodec
import com.thezone.probe.R

/**
 * Raises a government-style emergency alert on this phone when a verified ALERT
 * packet arrives over the mesh. WARNING / EXTREME get the full treatment — a
 * loud alarm sound, strong vibration, DND bypass and a full-screen take-over
 * ([AlertActivity]); lower categories get a prominent notification.
 */
object AlertNotifier {

    private const val CH_EXTREME = "zone_alert_extreme"
    private const val CH_NOTICE = "zone_alert_notice"

    fun channels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CH_EXTREME) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CH_EXTREME, "Emergency alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Loud, full-screen alerts for warnings and extreme danger."
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 800)
                    setBypassDnd(true)
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setSound(
                        Uri.parse("android.resource://${context.packageName}/${R.raw.zone_alert}"),
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                },
            )
        }
        if (mgr.getNotificationChannel(CH_NOTICE) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CH_NOTICE, "Alerts & advisories", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Information, advisory and watch-level alerts."
                    enableVibration(true)
                    setBypassDnd(true)
                },
            )
        }
    }

    fun notifyId(rec: AlertRecord): Int = ("alert_" + rec.contentIdHex).hashCode()

    fun show(context: Context, rec: AlertRecord) {
        channels(context)
        val loud = rec.category >= PacketCodec.ALERT_WARNING
        val open = PendingIntent.getActivity(
            context, notifyId(rec),
            AlertActivity.intent(context, rec),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val b = NotificationCompat.Builder(context, if (loud) CH_EXTREME else CH_NOTICE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("${AlertText.categoryName(rec.category)} · ${AlertText.label(rec.phraseCode)}")
            .setContentText(AlertText.full(rec.phraseCode))
            .setStyle(NotificationCompat.BigTextStyle().bigText(AlertText.full(rec.phraseCode)))
            .setPriority(if (loud) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(0xFFB5311B.toInt())
            .setColorized(true)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOngoing(loud)
        if (loud) b.setFullScreenIntent(open, true)
        context.getSystemService(NotificationManager::class.java).notify(notifyId(rec), b.build())
    }

    fun cancel(context: Context, rec: AlertRecord) {
        context.getSystemService(NotificationManager::class.java).cancel(notifyId(rec))
    }
}
