package com.thezone.core

import com.thezone.packet.Packet
import kotlin.math.roundToInt

/**
 * Barometric *relative* altitude (PRD §6). Absolute altitude is worthless; the
 * delta against a baseline captured at launch is the whole value — it works
 * indoors where GPS is dead, and it tells a rescuer basement from rooftop.
 *
 * Pure Kotlin. Zero Android imports. `PressureReader` is the thin Android glue.
 */
object Barometry {

    const val SEA_LEVEL_HPA = 1013.25

    /** International barometric formula — the same one as `SensorManager.getAltitude`. */
    fun altitudeMeters(pressureHpa: Double, seaLevelHpa: Double = SEA_LEVEL_HPA): Double =
        44_330.0 * (1.0 - Math.pow(pressureHpa / seaLevelHpa, 1.0 / 5.255))

    /** Metres gained going from [fromHpa] to [toHpa] (lower pressure = higher). */
    fun deltaMeters(fromHpa: Double, toHpa: Double): Double =
        altitudeMeters(toHpa) - altitudeMeters(fromHpa)
}

/**
 * Tracks relative altitude from a stream of raw pressure samples, plus the trend
 * across the last [TREND_TRANSMISSIONS] outgoing transmissions (a rising trend
 * under RISING_WATER is the automatic drowning escalation, PRD §6).
 *
 * Absence of a barometer is a first-class flag — [deltaByte] returns
 * [Packet.NO_BAROMETER], never a false zero.
 */
class RelativeAltimeter(
    /** EMA weight for a new pressure sample. Raw baro is spiky; smooth for ±1 m. */
    private val smoothingAlpha: Double = 0.15,
    /** trend >= this (metres over the window) counts as "rising". */
    val risingTrendThresholdMeters: Int = 2,
    private val seaLevelHpa: Double = Barometry.SEA_LEVEL_HPA,
) {

    private var hasBarometer = true
    private var smoothedPressureHpa: Double? = null
    private var baselineAltitudeMeters: Double? = null
    private val transmittedDeltas = ArrayDeque<Int>()

    fun markNoBarometer() {
        hasBarometer = false
    }

    fun hasBarometer(): Boolean = hasBarometer

    /** Feed one raw pressure reading, hPa. The first sample sets the baseline. */
    fun onPressure(pressureHpa: Double) {
        hasBarometer = true
        val prev = smoothedPressureHpa
        smoothedPressureHpa =
            if (prev == null) pressureHpa
            else smoothingAlpha * pressureHpa + (1 - smoothingAlpha) * prev
        if (baselineAltitudeMeters == null) {
            baselineAltitudeMeters = altitudeOf(smoothedPressureHpa!!)
        }
    }

    /** Re-capture the baseline from the current smoothed pressure ("I'm at ground level now"). */
    fun captureBaseline() {
        smoothedPressureHpa?.let { baselineAltitudeMeters = altitudeOf(it) }
    }

    fun baselineAltitudeMeters(): Double? = baselineAltitudeMeters

    /** Current altitude above baseline, metres. null when no barometer or no data yet. */
    fun deltaMeters(): Double? {
        if (!hasBarometer) return null
        val p = smoothedPressureHpa ?: return null
        val b = baselineAltitudeMeters ?: return null
        return altitudeOf(p) - b
    }

    /** The signed int8 to put in the packet: clamped ±127, or [Packet.NO_BAROMETER]. */
    fun deltaByte(): Int =
        deltaMeters()?.roundToInt()?.coerceIn(-127, 127) ?: Packet.NO_BAROMETER

    /** Call once per outgoing heartbeat so the trend window advances at tx cadence. */
    fun markTransmitted() {
        val d = deltaMeters()?.roundToInt() ?: return
        transmittedDeltas.addLast(d)
        while (transmittedDeltas.size > TREND_TRANSMISSIONS + 1) transmittedDeltas.removeFirst()
    }

    /** Metres changed across the last [TREND_TRANSMISSIONS] transmissions. Positive = climbing. */
    fun trendMeters(): Int {
        if (!hasBarometer || transmittedDeltas.size < 2) return 0
        return (transmittedDeltas.last() - transmittedDeltas.first()).coerceIn(-128, 127)
    }

    fun isRising(): Boolean = hasBarometer && trendMeters() >= risingTrendThresholdMeters

    private fun altitudeOf(hPa: Double): Double = Barometry.altitudeMeters(hPa, seaLevelHpa)

    companion object {
        const val TREND_TRANSMISSIONS = 3
    }
}
