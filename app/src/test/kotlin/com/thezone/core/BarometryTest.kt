package com.thezone.core

import com.thezone.packet.Packet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class BarometryTest {

    @Test
    fun altitudeFormula_isZeroAtSeaLevelAndPositiveAsPressureDrops() {
        assertEquals(0.0, Barometry.altitudeMeters(1013.25), 0.01)
        // ~ -8.3 m per hPa near the ground
        assertTrue(Barometry.altitudeMeters(1000.0) in 100.0..125.0)
        assertTrue(Barometry.altitudeMeters(1020.0) < 0.0)
    }

    @Test
    fun relativeDelta_isMeasuredFromTheFirstSample() {
        val alt = RelativeAltimeter(smoothingAlpha = 1.0) // no smoothing lag for the assertion
        alt.onPressure(1000.0) // baseline here
        assertEquals(0.0, alt.deltaMeters()!!, 0.01)

        alt.onPressure(999.0) // ~ +8.3 m
        assertTrue(alt.deltaMeters()!! in 7.0..9.5)
    }

    @Test
    fun oneFloorUp_readsAboutThreeMetres() {
        val alt = RelativeAltimeter(smoothingAlpha = 1.0)
        val groundHpa = 1005.0
        alt.onPressure(groundHpa)

        // pressure at +3 m above ground
        val upHpa = pressureAtDeltaMeters(groundHpa, 3.0)
        alt.onPressure(upHpa)

        assertTrue("expected ~3 m, got ${alt.deltaMeters()}", abs(alt.deltaMeters()!! - 3.0) <= 1.0)
        assertTrue(alt.deltaByte() in 2..4)
    }

    @Test
    fun smoothingRejectsSpikes() {
        val alt = RelativeAltimeter(smoothingAlpha = 0.15)
        alt.onPressure(1000.0)
        val rng = Random(7)
        repeat(60) { alt.onPressure(1000.0 + rng.nextDouble(-0.4, 0.4)) }
        alt.onPressure(1000.0 + 3.0) // one big spike (~ -25 m)
        // a single spike must not move the smoothed delta more than a few metres
        assertTrue("spike leaked through: ${alt.deltaMeters()}", abs(alt.deltaMeters()!!) < 6.0)
    }

    @Test
    fun trendIsChangeAcrossLastThreeTransmissions() {
        val alt = RelativeAltimeter(smoothingAlpha = 1.0)
        val ground = 1000.0
        alt.onPressure(ground); alt.markTransmitted()                       // delta 0
        alt.onPressure(pressureAtDeltaMeters(ground, 2.0)); alt.markTransmitted()
        alt.onPressure(pressureAtDeltaMeters(ground, 4.0)); alt.markTransmitted()
        alt.onPressure(pressureAtDeltaMeters(ground, 6.0)); alt.markTransmitted()

        // window holds deltas ~[0,2,4,6]; change across the last 3 tx ~ +6
        assertTrue("trend was ${alt.trendMeters()}", alt.trendMeters() in 5..7)
        assertTrue(alt.isRising())
    }

    @Test
    fun flatAltitudeHasZeroTrendAndIsNotRising() {
        val alt = RelativeAltimeter(smoothingAlpha = 1.0)
        repeat(5) { alt.onPressure(1000.0); alt.markTransmitted() }
        assertEquals(0, alt.trendMeters())
        assertFalse(alt.isRising())
    }

    @Test
    fun noBarometer_isAFlagNeverAFalseZero() {
        val alt = RelativeAltimeter()
        alt.markNoBarometer()

        assertFalse(alt.hasBarometer())
        assertEquals(null, alt.deltaMeters())
        assertEquals(Packet.NO_BAROMETER, alt.deltaByte())
        assertEquals(0, alt.trendMeters())
        assertFalse(alt.isRising())
    }

    @Test
    fun descendingTrendIsNegativeAndNotRising() {
        val alt = RelativeAltimeter(smoothingAlpha = 1.0)
        val ground = 1000.0
        listOf(6.0, 4.0, 2.0, 0.0).forEach {
            alt.onPressure(pressureAtDeltaMeters(ground, it)); alt.markTransmitted()
        }
        assertTrue(alt.trendMeters() < 0)
        assertFalse(alt.isRising())
    }

    /** Invert the barometric formula: pressure that sits [deltaMeters] above [groundHpa]. */
    private fun pressureAtDeltaMeters(groundHpa: Double, deltaMeters: Double): Double {
        val groundAlt = Barometry.altitudeMeters(groundHpa)
        val targetAlt = groundAlt + deltaMeters
        // altitude = 44330 * (1 - (p/1013.25)^(1/5.255))  ->  solve for p
        val ratio = Math.pow(1.0 - targetAlt / 44_330.0, 5.255)
        return ratio * Barometry.SEA_LEVEL_HPA
    }
}
