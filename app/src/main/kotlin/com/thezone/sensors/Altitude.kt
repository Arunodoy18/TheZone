package com.thezone.sensors

import com.thezone.core.RelativeAltimeter
import com.thezone.packet.Packet

/**
 * Process-wide relative-altitude state. Owns the one [RelativeAltimeter];
 * [PressureReader] feeds it raw samples and [com.thezone.demo.HeartbeatSource]
 * reads the published fields when building a packet.
 */
object Altitude {

    private val altimeter = RelativeAltimeter()

    @Volatile var hasBarometer: Boolean = false
        private set

    /** Signed int8 for the packet: metres above baseline, or [Packet.NO_BAROMETER]. */
    @Volatile var deltaByte: Int = Packet.NO_BAROMETER
        private set

    /** Metres change across the last 3 transmissions. */
    @Volatile var trendMeters: Int = 0
        private set

    @Volatile var rising: Boolean = false
        private set

    @Volatile var baselineMeters: Double? = null
        private set

    fun onPressure(hPa: Double) {
        altimeter.onPressure(hPa)
        refresh()
    }

    fun markNoBarometer() {
        altimeter.markNoBarometer()
        refresh()
    }

    /** Advance the trend window — called once per outgoing heartbeat. */
    fun markTransmitted() {
        altimeter.markTransmitted()
        refresh()
    }

    /** "I'm at ground level now" — reset the baseline to the current reading. */
    fun resetBaseline() {
        altimeter.captureBaseline()
        refresh()
    }

    private fun refresh() {
        hasBarometer = altimeter.hasBarometer()
        deltaByte = altimeter.deltaByte()
        trendMeters = altimeter.trendMeters()
        rising = altimeter.isRising()
        baselineMeters = altimeter.baselineAltitudeMeters()
    }
}
