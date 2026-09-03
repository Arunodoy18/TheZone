package com.thezone.core

import com.thezone.packet.DeviceIdentity
import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec
import com.thezone.packet.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CorroborationScorerTest {

    private val rng = Random(0xC0DE)

    private fun report(
        deltaLat: Int = 500, deltaLon: Int = 600,
        status: Int = Status.INJURED.code,
        severity: Int = 10,
        altDelta: Int = 2,
        altTrend: Int = 0,
        hop: Int = 0,
        hopsSeen: Set<Int> = setOf(0),
    ): StoredReport {
        val id = DeviceIdentity(ByteArray(32) { rng.nextInt().toByte() })
        val p = Packet(
            version = 1, type = 0, deviceId = id.deviceId,
            deltaLat = deltaLat, deltaLon = deltaLon,
            status = status, severity = severity, casualties = 0,
            timestampMinutes = 100, batteryLevel = 8, hopCount = hop,
            nextExpectedTxSeconds = 10, altDelta = altDelta, altTrend = altTrend,
        )
        val bytes = PacketCodec.encode(p, id)
        return StoredReport(
            contentId = PacketCodec.contentId(bytes).joinToString("") { "%02x".format(it) },
            bytes = bytes, packet = PacketCodec.decode(bytes),
            firstHeardAtMillis = 0, lastHeardAtMillis = 0,
            bestRssiDbm = -60, lastRssiDbm = -60,
            receivedHopCount = hop, hopsSeen = hopsSeen, timesHeard = 1, isOwn = false,
        )
    }

    @Test
    fun oneDeviceIsLowConfidence_threeIndependentIsHigh() {
        val single = CorroborationScorer.scoreCells(listOf(report())).single()
        val trio = CorroborationScorer.scoreCells(List(3) { report() }).single()

        assertTrue("single device: ${single.confidence}", single.confidence < 0.45)
        assertTrue("three devices: ${trio.confidence}", trio.confidence > single.confidence + 0.2)
        assertEquals(1, single.distinctDevices)
        assertEquals(3, trio.distinctDevices)
    }

    private fun devHex(r: StoredReport) = r.packet.deviceId.joinToString("") { "%02x".format(it) }

    @Test
    fun anUnverifiedResponderClaimIsJustAnotherPhone() {
        // a RESPONDER byte whose auth didn't check out against the shared key
        // (not in the verified set) buys nothing extra
        val responder = report(status = Status.RESPONDER.code)
        val scored = CorroborationScorer.scoreCells(listOf(responder), verifiedResponders = emptySet()).single()
        val ordinary = CorroborationScorer.scoreCells(listOf(report(status = Status.INJURED.code))).single()
        assertTrue(!scored.hasVerifiedReporter)
        assertEquals(ordinary.confidence, scored.confidence, 1e-9)
    }

    @Test
    fun aVerifiedResponderGetsTheFullWeight() {
        val responder = report(status = Status.RESPONDER.code)
        val unverified = CorroborationScorer.scoreCells(listOf(responder)).single()
        val verified = CorroborationScorer
            .scoreCells(listOf(responder), verifiedResponders = setOf(devHex(responder)))
            .single()
        assertTrue(verified.hasVerifiedReporter)
        assertTrue(
            "verified ${verified.confidence} vs unverified ${unverified.confidence}",
            verified.confidence >= unverified.confidence + 0.24,
        )
    }

    @Test
    fun sybilFloodOfImplausiblePacketsDoesNotInflateConfidence() {
        // 8 fabricated ids, all physically impossible (alt pinned at the clamp)
        val flood = List(8) { report(status = Status.RISING_WATER.code, altTrend = -9, altDelta = 127) }
        val c = CorroborationScorer.scoreCells(flood).single()
        assertEquals(8, c.implausibleReports)
        assertTrue("junk flood confidence ${c.confidence}", c.confidence < 0.1)
    }

    @Test
    fun pathDiversityRaisesConfidence() {
        val oneRoute = CorroborationScorer.scoreCells(listOf(report(hopsSeen = setOf(2)))).single()
        val twoRoutes = CorroborationScorer.scoreCells(listOf(report(hopsSeen = setOf(1, 3)))).single()
        assertTrue(twoRoutes.confidence > oneRoute.confidence)
        assertEquals(2, twoRoutes.pathDiversity)
    }

    @Test
    fun implausibleReportsDragConfidenceDown() {
        // "rising water" but altitude plummeting, and altitude pinned at the clamp
        val bad = List(3) {
            report(status = Status.RISING_WATER.code, altTrend = -8, altDelta = 127)
        }
        val good = List(3) { report(status = Status.RISING_WATER.code, altTrend = 4, altDelta = 6) }

        val badC = CorroborationScorer.scoreCells(bad).single()
        val goodC = CorroborationScorer.scoreCells(good).single()
        assertEquals(3, badC.implausibleReports)
        assertTrue(badC.confidence < goodC.confidence)
    }

    @Test
    fun plausibilityRules() {
        fun p(status: Int, altDelta: Int, altTrend: Int, hop: Int = 0) = Packet(
            1, 0, ByteArray(6), 0, 0, status, 5, 0, 0, 8, hop, 10, altDelta, altTrend,
        )
        assertTrue(CorroborationScorer.plausible(p(Status.RISING_WATER.code, 4, 3)))
        assertTrue(!CorroborationScorer.plausible(p(Status.RISING_WATER.code, 4, -6)))
        assertTrue(!CorroborationScorer.plausible(p(Status.TRAPPED_DEBRIS.code, 40, 0)))
        assertTrue(!CorroborationScorer.plausible(p(Status.INJURED.code, 127, 0)))
        assertTrue(!CorroborationScorer.plausible(p(Status.INJURED.code, 5, 0, hop = 15)))
    }

    @Test
    fun cellsAreKeyedByPosition() {
        val here = List(2) { report(deltaLat = 100, deltaLon = 100) }
        val there = List(2) { report(deltaLat = 5000, deltaLon = 5000) }
        val cells = CorroborationScorer.scoreCells(here + there)
        assertEquals(2, cells.size)
    }
}
