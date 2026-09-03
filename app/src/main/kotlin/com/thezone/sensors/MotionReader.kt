package com.thezone.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Thin Android glue: feeds acceleration magnitude to [Motion] so the heartbeat
 * can infer TRAPPED from sustained immobility. Mirrors [PressureReader].
 *
 * Prefers TYPE_LINEAR_ACCELERATION (gravity already removed → |a| ≈ 0 at rest);
 * falls back to TYPE_ACCELEROMETER and subtracts standard gravity. If the phone
 * has neither, it flags that once and does nothing — status inference then skips
 * immobility, exactly as before.
 */
class MotionReader(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val linear: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val raw: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val sensor: Sensor? = linear ?: raw
    private val usingRaw = linear == null && raw != null

    fun start() {
        val s = sensor
        if (s == null || sensorManager == null) {
            Motion.markNoAccelerometer()
            return
        }
        Motion.markHasAccelerometer()
        // ~5 Hz is plenty for a minutes-scale stillness call and easy on the battery
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val mag = sqrt(x * x + y * y + z * z).toDouble()
        val motion = if (usingRaw) kotlin.math.abs(mag - SensorManager.STANDARD_GRAVITY) else mag
        Motion.onSample(motion, System.currentTimeMillis())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
