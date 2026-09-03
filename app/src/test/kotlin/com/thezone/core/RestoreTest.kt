package com.thezone.core

import com.thezone.packet.DeviceIdentity
import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Tier 0 crash-safety hooks: ReportStore.restore, SilenceEvaluator.seed / restoreCellLosses. */
class RestoreTest {

    private val rng = Random(0xC0FFEE)

    private fun identity() =
        DeviceIdentity(ByteArray(DeviceIdentity.KEY_BYTES) { rng.nextInt().toByte() })

    private fun bytes(
        id: DeviceIdentity = identity(),
        battery: Int = 12,
        nextTx: Int = 10,
        deltaLat: Int = 45,
        deltaLon: Int = 45,
    ): ByteArray = PacketCodec.encode(
        Packet(
            version = Packet.PROTOCOL_VERSION,
            type = Packet.TYPE_STATUS,
            deviceId = id.deviceId,
            deltaLat = deltaLat,
            deltaLon = deltaLon,
            status = 2,
            severity = 6,
            casualties = 0,
            timestampMinutes = 1000,
            batteryLevel = battery,
            hopCount = 0,
            nextExpectedTxSeconds = nextTx,
            altDelta = 0,
            altTrend = 0,
        ),
        id,
    )

    @Test
    fun `restore replaces store contents and keeps metadata`() {
        val a = ReportStore()
        a.accept(bytes(), rssiDbm = -55, receivedAtMillis = 1_000)
        a.accept(bytes(), rssiDbm = -70, receivedAtMillis = 2_000)
        val snapshot = a.all()
        assertEquals(2, snapshot.size)

        val b = ReportStore()
        b.accept(bytes(), rssiDbm = -80, receivedAtMillis = 9_999) // pre-existing, must be dropped
        b.restore(snapshot)

        assertEquals(2, b.size)
        val restored = b.all().sortedBy { it.firstHeardAtMillis }
        assertEquals(snapshot.sortedBy { it.firstHeardAtMillis }.map { it.contentId }, restored.map { it.contentId })
        assertEquals(1_000L, restored[0].firstHeardAtMillis)
        assertEquals(-55, restored[0].bestRssiDbm)
    }

    @Test
    fun `seeded stale track is classified as unexpected silence on the next tick`() {
        var now = 10_000_000L
        val ev = SilenceEvaluator(nowMillis = { now })
        val dev = "beef"
        val p = PacketCodec.decode(bytes(battery = 12, nextTx = 10)) // 12% > critical(10), 10s promise

        // heard a long time ago, then a reboot — seed it, don't onPacket it
        ev.seed(dev, p, lastHeardAtMillis = now - 60 * 60 * 1000) // 1h ago
        val transitions = ev.tick(now).transitions
        assertEquals(SilenceState.UNEXPECTED_SILENCE, ev.deviceState(dev))
        assertTrue(transitions.any { it.deviceIdHex == dev && it.to == SilenceState.UNEXPECTED_SILENCE })
    }

    @Test
    fun `onPacket would have rejected the same stale report as a relay echo`() {
        var now = 10_000_000L
        val ev = SilenceEvaluator(nowMillis = { now })
        val p = PacketCodec.decode(bytes())
        ev.onPacket("beef", p, receivedAtMillis = now) // stamp says ~ EventClock epoch, far older than 90s
        assertEquals(0, ev.snapshot(now).size)
    }

    @Test
    fun `restored cell loss is present and not re-emitted`() {
        var now = 5_000_000L
        val ev = SilenceEvaluator(nowMillis = { now })
        val loss = CellLoss(
            cell = GridCell(1, -2),
            deviceCount = 12,
            silentCount = 12,
            firstSilentAtMillis = now - 5_000,
            lastSilentAtMillis = now - 1_000,
            detectedAtMillis = now - 500,
        )
        ev.restoreCellLosses(listOf(loss))
        assertEquals(listOf(loss), ev.cellLosses())
        assertTrue(ev.tick(now).newCellLosses.isEmpty())
    }
}
