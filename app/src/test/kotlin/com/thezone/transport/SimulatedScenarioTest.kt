package com.thezone.transport

import com.thezone.core.SilenceEvaluator
import com.thezone.core.SilenceState
import com.thezone.packet.PacketCodec
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * End-to-end: the simulator's scripted events, fed through the real
 * [SilenceEvaluator], must produce a CELL_LOSS for the doomed building and tell a
 * healthy-battery blackout (UNEXPECTED) from a critical-battery one (EXPECTED).
 * Runs a compressed scenario in real time (~20 s).
 */
class SimulatedScenarioTest {

    @Test
    fun scriptedEvents_produceCellLossAndTheT3T4Distinction() {
        val transport = SimulatedTransport(nodeCount = 40, scenarioSeconds = 6)
        // fast grace so the ~2 s beacons resolve inside the test window
        val evaluator = SilenceEvaluator(graceFloorMillis = 3_000L, freshnessWindowMillis = 120_000L)
        val lastBattery = ConcurrentHashMap<String, Int>()

        transport.onPacket { inbound ->
            val p = runCatching { PacketCodec.decode(inbound.bytes) }.getOrNull() ?: return@onPacket
            val dev = p.deviceId.joinToString("") { "%02x".format(it) }
            lastBattery[dev] = com.thezone.packet.BatteryScale.nibbleToPercent(p.batteryLevel)
            evaluator.onPacket(dev, p, inbound.receivedAtMillis)
        }

        transport.start()

        val deadline = System.currentTimeMillis() + 22_000
        var cellLoss = false
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(400)
            if (evaluator.tick().newCellLosses.any { it.silentCount >= 3 }) cellLoss = true
        }
        transport.shutdown()

        assertTrue("expected a CELL_LOSS from the doomed building", cellLoss)

        val transitions = evaluator.transitions()
        val unexpectedHealthy = transitions.any {
            it.to == SilenceState.UNEXPECTED_SILENCE && it.batteryPercent >= 40
        }
        val expectedCritical = transitions.any {
            it.to == SilenceState.EXPECTED_SILENCE && it.batteryPercent <= 10
        }
        assertTrue("a healthy-battery blackout should read UNEXPECTED", unexpectedHealthy)
        assertTrue("a critical-battery blackout should read EXPECTED", expectedCritical)
    }
}
