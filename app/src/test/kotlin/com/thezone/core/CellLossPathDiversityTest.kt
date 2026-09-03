package com.thezone.core

import com.thezone.packet.BatteryScale
import com.thezone.packet.DeviceIdentity
import com.thezone.packet.EventClock
import com.thezone.packet.Packet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The optional Sybil guard on CELL_LOSS (requireCellPathDiversity). */
class CellLossPathDiversityTest {

    private val rng = Random(0xBADC0DE)

    private fun packet(id: DeviceIdentity, sentAt: Long, hop: Int) = Packet(
        version = Packet.PROTOCOL_VERSION, type = Packet.TYPE_STATUS, deviceId = id.deviceId,
        deltaLat = 1000, deltaLon = 2000, // one 90-unit cell
        status = 2, severity = 8, casualties = 0,
        timestampMinutes = EventClock.stampMinutes(sentAt),
        batteryLevel = BatteryScale.percentToNibble(80), hopCount = hop,
        nextExpectedTxSeconds = 10, altDelta = 0, altTrend = 0,
    )

    /** Drive N devices in one cell to unexpected silence; each heard at its given hop. */
    private fun collapseCell(guard: Boolean, hops: List<Int>): SilenceEvaluator {
        var clock = EventClock.EVENT_EPOCH_MILLIS + 3_600_000L
        val ev = SilenceEvaluator(requireCellPathDiversity = guard, nowMillis = { clock })
        hops.forEach { hop ->
            val id = DeviceIdentity(ByteArray(DeviceIdentity.KEY_BYTES) { rng.nextInt().toByte() })
            ev.onPacket(id.deviceId.joinToString("") { "%02x".format(it) }, packet(id, clock, hop))
        }
        clock += 45_000 // all quiet, past 4x the 10 s promise
        ev.tick(clock)
        return ev
    }

    @Test
    fun `guard off — a one-hop cluster still collapses (demo behaviour unchanged)`() {
        assertEquals(1, collapseCell(guard = false, hops = listOf(3, 3, 3, 3)).cellLosses().size)
    }

    @Test
    fun `guard on — a cluster seen only via one relay does NOT collapse`() {
        assertTrue(collapseCell(guard = true, hops = listOf(3, 3, 3, 3)).cellLosses().isEmpty())
    }

    @Test
    fun `guard on — hop diversity lets a real collapse through`() {
        assertEquals(1, collapseCell(guard = true, hops = listOf(1, 2, 2, 3)).cellLosses().size)
    }

    @Test
    fun `guard on — a near-direct member lets a collapse through`() {
        assertEquals(1, collapseCell(guard = true, hops = listOf(0, 4, 4, 4)).cellLosses().size)
    }
}
