package com.thezone.core

import com.thezone.packet.Packet
import com.thezone.packet.Status
import org.junit.Assert.assertEquals
import org.junit.Test

class TriageScorerTest {

    private val now = 10_000_000L

    private fun entry(
        id: String,
        status: Int = Status.UNKNOWN.code,
        severity: Int = 5,
        altDelta: Int = Packet.NO_BAROMETER,
        altTrend: Int = 0,
        batteryPercent: Int = 60,
        lastHeardAtMillis: Long = now - 1_000,
        silence: SilenceState = SilenceState.ALIVE,
        unexpectedSinceMillis: Long? = null,
    ) = TriageEntry(
        deviceIdHex = id,
        status = status,
        severity = severity,
        casualties = 0,
        altDelta = altDelta,
        altTrend = altTrend,
        batteryPercent = batteryPercent,
        hopsFromOrigin = 1,
        lastHeardAtMillis = lastHeardAtMillis,
        lastRssiDbm = -60,
        silence = silence,
        unexpectedSinceMillis = unexpectedSinceMillis,
    )

    @Test
    fun rankOrderMatchesPrdSection5() {
        val risingWater = entry("a", status = Status.RISING_WATER.code, altTrend = 3, severity = 1)
        val justLost = entry(
            "b",
            silence = SilenceState.UNEXPECTED_SILENCE,
            unexpectedSinceMillis = now - 60_000,
            severity = 1,
        )
        val trappedBelow = entry("c", status = Status.TRAPPED_DEBRIS.code, altDelta = -4, severity = 1)
        val batteryCritical = entry("d", batteryPercent = 5, severity = 15)
        val ordinaryHighSeverity = entry("e", severity = 15)

        val sorted = TriageScorer.sort(
            listOf(ordinaryHighSeverity, batteryCritical, trappedBelow, justLost, risingWater),
            now,
        ).map { it.deviceIdHex }

        assertEquals(listOf("a", "b", "c", "d", "e"), sorted)
    }

    @Test
    fun risingWaterOnlyRanksTopWhenActuallyClimbing() {
        val climbing = entry("climb", status = Status.RISING_WATER.code, altTrend = 2)
        val notClimbing = entry("flat", status = Status.RISING_WATER.code, altTrend = 0)
        assertEquals(5, TriageScorer.tier(climbing, now))
        assertEquals(1, TriageScorer.tier(notClimbing, now)) // falls through to "everything else"
    }

    @Test
    fun unexpectedSilenceOlderThanFiveMinutesDropsOutOfTier4() {
        val fresh = entry("f", silence = SilenceState.UNEXPECTED_SILENCE, unexpectedSinceMillis = now - 60_000)
        val stale = entry("s", silence = SilenceState.UNEXPECTED_SILENCE, unexpectedSinceMillis = now - 6 * 60_000)
        assertEquals(4, TriageScorer.tier(fresh, now))
        assertEquals(1, TriageScorer.tier(stale, now))
    }

    @Test
    fun trappedDebris_needsNegativeAltitude_notJustAbsentBarometer() {
        val belowGrade = entry("below", status = Status.TRAPPED_DEBRIS.code, altDelta = -2)
        val noBaro = entry("nobaro", status = Status.TRAPPED_DEBRIS.code, altDelta = Packet.NO_BAROMETER)
        assertEquals(3, TriageScorer.tier(belowGrade, now))
        assertEquals(1, TriageScorer.tier(noBaro, now))
    }

    @Test
    fun withinATier_sortsBySeverityThenRecency() {
        val a = entry("a", severity = 4, lastHeardAtMillis = now - 5_000)
        val b = entry("b", severity = 9, lastHeardAtMillis = now - 9_000)
        val c = entry("c", severity = 9, lastHeardAtMillis = now - 1_000)
        assertEquals(listOf("c", "b", "a"), TriageScorer.sort(listOf(a, b, c), now).map { it.deviceIdHex })
    }
}
