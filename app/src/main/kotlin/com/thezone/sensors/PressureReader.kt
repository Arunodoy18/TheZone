package com.thezone.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Thin Android glue: registers a `TYPE_PRESSURE` listener and forwards raw hPa
 * readings to [Altitude]. If the phone has no barometer (all our no-baro phones),
 * it flags that once and does nothing else — never a false zero.
 */
class PressureReader(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val pressureSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val hasBarometer: Boolean get() = pressureSensor != null

    fun start() {
        val s = pressureSensor
        if (s == null || sensorManager == null) {
            Altitude.markNoBarometer()
            return
        }
        sensorManager.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_PRESSURE) {
            Altitude.onPressure(event.values[0].toDouble())
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
