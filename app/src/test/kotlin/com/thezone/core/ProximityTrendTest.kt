package com.thezone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityTrendTest {

    @Test
    fun `rising rssi reads warmer`() {
        val t = ProximityTrend()
        var now = 0L
        // -90 -> -60 over 6 s
        for (r in -90..-60 step 5) { t.add(r, now); now += 1000 }
        assertTrue(t.slopeDbPerSec() > 0.6)
        assertEquals(ProximityTrend.Reading.WARMER, t.reading())
    }

    @Test
    fun `falling rssi reads colder`() {
        val t = ProximityTrend()
        var now = 0L
        for (r in -60 downTo -90 step 5) { t.add(r, now); now += 1000 }
        assertEquals(ProximityTrend.Reading.COLDER, t.reading())
    }

    @Test
    fun `steady rssi reads holding, and few samples are neutral`() {
        val t = ProximityTrend()
        assertEquals(0.0, t.slopeDbPerSec(), 0.0)
        assertEquals(ProximityTrend.Reading.HOLDING, t.reading())
        var now = 0L
        repeat(6) { t.add(-72, now); now += 1000 }
        assertTrue(kotlin.math.abs(t.slopeDbPerSec()) < 0.6)
        assertEquals(ProximityTrend.Reading.HOLDING, t.reading())
    }

    @Test
    fun `stale samples fall out of the window`() {
        val t = ProximityTrend(windowMillis = 5000)
        t.add(-95, 0); t.add(-95, 1000); t.add(-95, 2000) // old, weak
        t.add(-55, 9000); t.add(-55, 10000); t.add(-55, 11000) // recent, strong, >5s later
        // only the recent flat cluster remains
        assertTrue(kotlin.math.abs(t.slopeDbPerSec()) < 0.6)
    }
}
