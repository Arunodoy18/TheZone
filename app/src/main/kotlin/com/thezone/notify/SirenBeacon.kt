package com.thezone.notify

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.thezone.core.AlertRecord
import com.thezone.probe.R

/**
 * Turns the phone itself into a physical alarm for a WARNING / EXTREME mesh
 * alert — loud looping siren, camera-torch strobe, continuous vibration, all at
 * once. This is the one alert-reach channel that needs no radio trick and no
 * app on the other end: anyone with working eyes or ears near the phone
 * notices, Zone installed or not.
 *
 * Fires on every phone that issues OR relays a loud alert (see
 * TransportController.issueAlert / ingest) — not just the origin — so the
 * physical warning spreads hop by hop the same way the packet does.
 *
 * All work is funneled onto the main thread: [start]/[stop] are called from
 * BLE callback and relay-pump threads, but MediaPlayer / CameraManager /
 * Handler all expect main-thread use.
 */
object SirenBeacon {

    private const val TAG = "TheZone"

    /** A bounded burst, not tied to the alert's full validity window — a real
     *  siren doesn't scream for the alert's entire 24h window, it announces. */
    private const val MAX_RUN_MS = 90_000L
    private const val STROBE_INTERVAL_MS = 300L

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var cameraManager: CameraManager? = null
    private var torchCameraId: String? = null
    private var torchOn = false
    private var appContextRef: Context? = null
    private var running = false

    private val strobeStep = object : Runnable {
        override fun run() {
            if (!running) return
            setTorch(!torchOn)
            handler.postDelayed(this, STROBE_INTERVAL_MS)
        }
    }

    fun start(context: Context, rec: AlertRecord) {
        val app = context.applicationContext
        handler.post { startOnMain(app, rec) }
    }

    /** Silence this device's siren/strobe/vibration. The Wi-Fi beacon is independent. */
    fun stop() {
        handler.post { stopOnMain() }
    }

    private fun startOnMain(app: Context, rec: AlertRecord) {
        if (running) return
        running = true
        appContextRef = app
        Log.w(TAG, "SIREN_BEACON start cat=${rec.category} phrase=${rec.phraseCode}")

        runCatching {
            val am = app.getSystemService(AudioManager::class.java)
            am?.let { it.setStreamVolume(AudioManager.STREAM_ALARM, it.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                val afd = app.resources.openRawResourceFd(R.raw.zone_alert)
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
                isLooping = true
                prepare()
                start()
            }
        }.onFailure { Log.w(TAG, "siren audio failed: ${it.message}") }

        runCatching {
            val v = app.getSystemService(Vibrator::class.java)
            val pattern = longArrayOf(0, 400, 200, 400, 200, 400, 600)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION") v?.vibrate(pattern, 0)
            }
        }.onFailure { Log.w(TAG, "siren vibrate failed: ${it.message}") }

        runCatching {
            val cm = app.getSystemService(CameraManager::class.java)
            cameraManager = cm
            torchCameraId = cm?.cameraIdList?.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            if (torchCameraId != null) handler.post(strobeStep)
        }.onFailure { Log.w(TAG, "siren torch failed: ${it.message}") }

        handler.postDelayed({ stopOnMain() }, MAX_RUN_MS)
    }

    private fun stopOnMain() {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        setTorch(false)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { appContextRef?.getSystemService(Vibrator::class.java)?.cancel() }
        Log.w(TAG, "SIREN_BEACON stop")
    }

    private fun setTorch(on: Boolean) {
        val cm = cameraManager ?: return
        val id = torchCameraId ?: return
        runCatching { cm.setTorchMode(id, on) }
        torchOn = on
    }
}
