package com.thezone.sensors

/**
 * Latest device location, best-effort. Mirrors [Altitude]: a plain singleton
 * snapshot the heartbeat reads, so nothing downstream imports Android location
 * types.
 *
 * Indoors GPS is dead — which is the disaster case — so a phone often has an old
 * fix or none. A stale fix from before someone walked into a building is still
 * the best guess for a pinned person; the heartbeat applies its own age cutoff.
 * No fix at all is fine: PACKET_SPEC localises a no-fix device from the phones
 * that relayed it.
 */
object Position {

    @Volatile private var lat: Double = 0.0
    @Volatile private var lon: Double = 0.0
    @Volatile private var accuracyM: Float = Float.MAX_VALUE
    @Volatile private var atMillis: Long = 0L
    @Volatile private var have: Boolean = false

    val hasFix: Boolean get() = have

    /** Snapshot, or null if this device has never had a fix. */
    fun snapshot(): Fix? = if (have) Fix(lat, lon, accuracyM, atMillis) else null

    fun onFix(lat: Double, lon: Double, accuracyMeters: Float, atMillis: Long) {
        // keep the newest reading
        if (atMillis < this.atMillis) return
        this.lat = lat
        this.lon = lon
        this.accuracyM = accuracyMeters
        this.atMillis = atMillis
        this.have = true
    }

    fun clear() {
        have = false
        atMillis = 0L
        accuracyM = Float.MAX_VALUE
    }

    data class Fix(
        val lat: Double,
        val lon: Double,
        val accuracyMeters: Float,
        val atMillis: Long,
    ) {
        fun ageMillis(now: Long = System.currentTimeMillis()): Long = now - atMillis
    }
}
