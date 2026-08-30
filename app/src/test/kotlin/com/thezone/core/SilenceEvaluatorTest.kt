package com.thezone.core

import com.thezone.packet.BatteryScale
import com.thezone.packet.DeviceIdentity
import com.thezone.packet.EventClock
import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SilenceEvaluatorTest {

    private val rng = Random(0xDEAD)
    private var clock = EventClock.EVENT_EPOCH_MILLIS + 3_600_000L // 1h in

    private fun idHex(id: DeviceIdentity) = id.deviceId.joinToString("") { "%02x".format(it) }

    private fun packet(
        id: DeviceIdentity,
        batteryPercent: Int,
        promisedSeconds: Int,
        sentAtMillis: Long,
        deltaLat: Int = Packet.NO_FIX,
        deltaLon: Int = Packet.NO_FIX,
        hop: Int = 0,
    ): Packet = Packet(
        version = Packet.PROTOCOL_VERSION,
        type = Packet.TYPE_STATUS,
        deviceId = id.deviceId,
        deltaLat = deltaLat,
        deltaLon = deltaLon,
        status = 2,
        severity = 5,
        casualties = 0,
        timestampMinutes = EventClock.stampMinutes(sentAtMillis),
        batteryLevel = BatteryScale.percentToNibble(batteryPercent),
        hopCount = hop,
        nextExpectedTxSeconds = promisedSeconds,
        altDelta = 0,
        altTrend = 0,
    )

    private fun newId() = DeviceIdentity(ByteArray(DeviceIdentity.KEY_BYTES) { rng.nextInt().toByte() })

    @Test
    fun aliveWhileWithinGrace_thenOverdue() {
        val ev = SilenceEvaluator(nowMillis = { clock })
        val a = newId()
        ev.onPacket(idHex(a), packet(a, batteryPercent = 80, promisedSeconds = 10, sentAtMillis = clock))

        clock += 12_000 // overdue_by = 12 - 10 = 2s, grace = max(8s, 20s) = 20s
        assertTrue(ev.tick(clock).transitions.isEmpty())
        assertEquals(SilenceState.ALIVE, ev.deviceState(idHex(a)))

        clock += 23_000 // overdue_by = 35 - 10 = 25s: past grace 20s, below 3x promised 30s
        ev.tick(clock)
        assertEquals(SilenceState.OVERDUE, ev.deviceState(idHex(a)))
    }

    /** T3: healthy battery, powered off -> UNEXPECTED_SILENCE, with the wall-clock time. */
    @Test
    fun unexpectedSilence_forHealthyBattery() {
        val ev = SilenceEvaluator(nowMillis = { clock })
        val a = newId()
        val heardAt = clock
        ev.onPacket(idHex(a), packet(a, batteryPercent = 80, promisedSeconds = 10, sentAtMillis = heardAt))

        clock += 45_000 // overdue_by = 35s > 3 * 10s
        val result = ev.tick(clock)

        assertEquals(SilenceState.UNEXPECTED_SILENCE, ev.deviceState(idHex(a)))
        val t = result.transitions.single { it.to == SilenceState.UNEXPECTED_SILENCE }
        assertEquals(idHex(a), t.deviceIdHex)
        assertEquals(clock, t.atMillis) // the moment to point at on stage
        assertEquals(80, t.batteryPercent)
        assertTrue(t.sinceLastHeardMillis in 44_000..46_000)
    }

    /** T4: same silence, but critical battery -> EXPECTED_SILENCE, never escalates. */
    @Test
    fun expectedSilence_forCriticalBattery_neverEscalates() {
        val ev = SilenceEvaluator(nowMillis = { clock })
        val a = newId()
        ev.onPacket(idHex(a), packet(a, batteryPercent = 6, promisedSeconds = 300, sentAtMillis = clock))

        clock += 20 * 60_000 // 20 min of silence — far past any multiple of 300s
        ev.tick(clock)
        assertEquals(SilenceState.EXPECTED_SILENCE, ev.deviceState(idHex(a)))

        clock += 60 * 60_000
        ev.tick(clock)
        assertEquals(
            "critical-battery silence must not become UNEXPECTED",
            SilenceState.EXPECTED_SILENCE,
            ev.deviceState(idHex(a)),
        )
    }

    @Test
    fun returnsFromTheDead_onNextPacket() {
        val ev = SilenceEvaluator(nowMillis = { clock })
        val a = newId()
        ev.onPacket(idHex(a), packet(a, 80, 10, clock))
        clock += 45_000
        ev.tick(clock)
        assertEquals(SilenceState.UNEXPECTED_SILENCE, ev.deviceState(idHex(a)))

        // heard again
        ev.onPacket(idHex(a), packet(a, 78, 10, clock))
        assertEquals(SilenceState.ALIVE, ev.deviceState(idHex(a)))
        val back = ev.transitions().last()
        assertEquals(SilenceState.UNEXPECTED_SILENCE, back.from)
        assertEquals(SilenceState.ALIVE, back.to)
    }

    @Test
    fun staleRelayedCopyDoesNotRefreshLiveness() {
        val ev = SilenceEvaluator(nowMillis = { clock })
        val a = newId()
        val originalSendTime = clock
        ev.onPacket(idHex(a), packet(a, 80, 10, originalSendTime))

        clock += 6 * 60_000 // 6 minutes later
        // a relay of that same 6-minute-old heartbeat arrives now (old stamp, higher hop)
        ev.onPacket(idHex(a), packet(a, 80, 10, originalSendTime, hop = 3), receivedAtMillis = clock)
        ev.tick(clock)

        assertEquals(
            "a stale relayed copy must not resurrect the device",
            SilenceState.UNEXPECTED_SILENCE,
            ev.deviceState(idHex(a)),
        )
    }

    @Test
    fun everyTransitionIsLoggedWithTimestamp() {
        val ev = SilenceEvaluator(nowMillis = { clock })
        val a = newId()
        ev.onPacket(idHex(a), packet(a, 80, 10, clock))
        clock += 33_000; ev.tick(clock)   // overdue_by 23s: ALIVE -> OVERDUE
        clock += 20_000; ev.tick(clock)   // overdue_by 43s: OVERDUE -> UNEXPECTED_SILENCE

        val log = ev.transitions()
        assertEquals(listOf(SilenceState.OVERDUE, SilenceState.UNEXPECTED_SILENCE), log.map { it.to })
        assertTrue(log.all { it.atMillis > EventClock.EVENT_EPOCH_MILLIS })
        assertTrue(log.zipWithNext().all { (a, b) -> a.atMillis <= b.atMillis })
    }

    /** The PS5 payoff: a cell where >=3 devices, >=80%, go unexpectedly silent together. */
    @Test
    fun cellLoss_firesForSimultaneousMassSilence() {
        val ev = SilenceEvaluator(nowMillis = { clock })
        // 4 devices in the same ~100 m cell (deltaLat/deltaLon within one 90-unit bucket)
        val ids = List(4) { newId() }
        ids.forEachIndexed { i, id ->
            ev.onPacket(idHex(id), packet(id, 80, 10, clock, deltaLat = 1000 + i, deltaLon = 2000 + i))
        }

        clock += 45_000 // all four stay quiet; their last heartbeats are already in
        val result = ev.tick(clock)

        assertEquals(1, result.newCellLosses.size)
        val loss = result.newCellLosses.single()
        assertEquals(4, loss.deviceCount)
        assertEquals(4, loss.silentCount)
        assertEquals(GridCell(Math.floorDiv(1000, 90), Math.floorDiv(2000, 90)), loss.cell)
        assertTrue(loss.lastSilentAtMillis - loss.firstSilentAtMillis <= 120_000)

        // idempotent — not re-emitted on the next tick
        clock += 10_000
        assertTrue(ev.tick(clock).newCellLosses.isEmpty())
        assertEquals(1, ev.cellLosses().size)
    }

    @Test
    fun cellLoss_doesNotFireForTwoDevices() {
        val ev = SilenceEvaluator(nowMillis = { clock })
        val ids = List(2) { newId() }
        ids.forEachIndexed { i, id ->
            ev.onPacket(idHex(id), packet(id, 80, 10, clock, deltaLat = 500 + i, deltaLon = 500 + i))
        }
        clock += 45_000
        assertTrue(ev.tick(clock).newCellLosses.isEmpty())
        assertTrue(ev.cellLosses().isEmpty())
    }

    @Test
    fun cellLoss_doesNotFireWhenSilencesAreSpreadOutInTime() {
        var c = EventClock.EVENT_EPOCH_MILLIS + 10_000_000L
        val ev = SilenceEvaluator(nowMillis = { c })
        val ids = List(3) { newId() }
        fun hearAll(vararg which: Int) = which.forEach {
            ev.onPacket(idHex(ids[it]), packet(ids[it], 80, 10, c, deltaLat = 700, deltaLon = 800), c)
        }

        hearAll(0, 1, 2)

        // dev0 goes silent now; dev1, dev2 kept alive
        c += 45_000; hearAll(1, 2); ev.tick(c)
        // ~200s later dev1 goes silent; dev2 still kept alive
        repeat(20) { c += 10_000; hearAll(2); ev.tick(c) }
        // ~200s more, dev2 finally lapses
        repeat(25) { c += 10_000; ev.tick(c) }

        // all three are silent eventually, 100% of the cell, but first->last spans ~400s
        assertTrue(ev.snapshot(c).all { it.state == SilenceState.UNEXPECTED_SILENCE })
        assertTrue("silences >120s apart must not read as one collapse", ev.cellLosses().isEmpty())
    }
}
