package com.thezone.packet

import kotlin.math.roundToInt

/**
 * Local-origin position encoding (docs/PACKET_SPEC.md "Position encoding").
 *
 *   delta = round((coord - origin) * 100_000)   // one int16 per axis
 *
 * ~1–2 m precision, ~±36 km range — correct for a district-scale disaster.
 * No fix is [Packet.NO_FIX] on *both* axes, never (0, 0): a device with no fix
 * that was heard by a device with one is localised from the relay path instead.
 */
object GeoPosition {

    // TODO(H8): set to the actual demo venue's coordinates before staging.
    // Placeholder: Rasuwagadhi border crossing, matching the seeded toll curve.
    const val ORIGIN_LAT = 28.2814
    const val ORIGIN_LON = 85.3779

    private const val SCALE = 100_000.0

    // -32768 is reserved for NO_FIX, so real deltas clamp to -32767.
    private const val DELTA_MIN = -32767
    private const val DELTA_MAX = 32767

    /** Encode one axis: (coord - origin) scaled, rounded, clamped to a safe int16. */
    fun encodeDelta(coord: Double, origin: Double): Int =
        ((coord - origin) * SCALE).roundToInt().coerceIn(DELTA_MIN, DELTA_MAX)

    fun latDelta(lat: Double): Int = encodeDelta(lat, ORIGIN_LAT)

    fun lonDelta(lon: Double): Int = encodeDelta(lon, ORIGIN_LON)

    fun toLat(delta: Int): Double = ORIGIN_LAT + delta / SCALE

    fun toLon(delta: Int): Double = ORIGIN_LON + delta / SCALE
}
