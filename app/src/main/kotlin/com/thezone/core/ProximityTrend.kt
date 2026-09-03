package com.thezone.core

/**
 * Turns a stream of RSSI samples into a hot/cold reading for Dig Here — "you're
 * getting closer, keep going" vs "you just walked away from it". RSSI alone gives
 * a noisy distance; its *slope* while you move is the useful signal.
 *
 * Pure Kotlin. Zero Android imports. Unit-tested on the JVM.
 */
class ProximityTrend(
    /** Only samples newer than this contribute to the slope. */
    private val windowMillis: Long = 6_000L,
    /** dB/s below which the reading is "holding", not warmer/colder. */
    private val flatSlopeDbPerSec: Double = 0.6,
) {

    private data class Sample(val rssi: Int, val atMillis: Long)

    private val lock = Any()
    private val samples = ArrayDeque<Sample>()

    fun add(rssiDbm: Int, atMillis: Long) = synchronized(lock) {
        if (rssiDbm >= 0) return@synchronized // 0/positive = no reading
        samples.addLast(Sample(rssiDbm, atMillis))
        val cutoff = atMillis - windowMillis
        while (samples.isNotEmpty() && samples.first().atMillis < cutoff) samples.removeFirst()
    }

    fun clear() = synchronized(lock) { samples.clear() }

    /** Least-squares slope of RSSI over the window, in dB per second. 0 if too few samples. */
    fun slopeDbPerSec(): Double = synchronized(lock) {
        if (samples.size < 3) return 0.0
        val t0 = samples.first().atMillis
        var n = 0.0; var sx = 0.0; var sy = 0.0; var sxx = 0.0; var sxy = 0.0
        for (s in samples) {
            val x = (s.atMillis - t0) / 1000.0
            val y = s.rssi.toDouble()
            n += 1; sx += x; sy += y; sxx += x * x; sxy += x * y
        }
        val denom = n * sxx - sx * sx
        if (denom == 0.0) 0.0 else (n * sxy - sx * sy) / denom
    }

    fun reading(): Reading {
        val slope = slopeDbPerSec()
        return when {
            slope > flatSlopeDbPerSec -> Reading.WARMER
            slope < -flatSlopeDbPerSec -> Reading.COLDER
            else -> Reading.HOLDING
        }
    }

    enum class Reading { WARMER, HOLDING, COLDER }
}
