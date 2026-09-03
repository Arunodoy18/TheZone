package com.thezone.packet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Position encoding — deltas from a per-incident origin (PACKET_SPEC "Position encoding"). */
class GeoPositionTest {

    @Test
    fun `1e-3 degree north of origin encodes to 100 units`() {
        val origin = 27.7172 // Kathmandu-ish; the origin is configurable, not fixed
        assertEquals(100, GeoPosition.encodeDelta(origin + 0.001, origin))
        assertEquals(-100, GeoPosition.encodeDelta(origin - 0.001, origin))
        assertEquals(0, GeoPosition.encodeDelta(origin, origin))
    }

    @Test
    fun `round-trips to roughly a metre at a custom origin`() {
        val originLat = 41.0082
        val originLon = 28.9784 // Istanbul
        val lat = originLat + 0.00042
        val lon = originLon - 0.00071
        val dLat = GeoPosition.encodeDelta(lat, originLat)
        val dLon = GeoPosition.encodeDelta(lon, originLon)
        // decode is origin + delta/1e5
        val backLat = originLat + dLat / 100_000.0
        val backLon = originLon + dLon / 100_000.0
        assertTrue(abs(backLat - lat) < 1e-5)
        assertTrue(abs(backLon - lon) < 1e-5)
    }

    @Test
    fun `real coordinates never collide with the NO_FIX sentinel`() {
        // farthest in-range point still clamps to -32767 / 32767, leaving -32768 (NO_FIX) free
        val d = GeoPosition.encodeDelta(0.0, 90.0) // way out of range, forces the clamp
        assertEquals(-32767, d)
        assertTrue(d != Packet.NO_FIX)
    }
}
