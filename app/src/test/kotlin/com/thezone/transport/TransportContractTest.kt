package com.thezone.transport

import com.thezone.packet.PacketCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The transports that don't need a radio are exercised on the JVM here, to keep
 * "all logic runs identically on any transport" (CLAUDE.md) honest. BleTransport
 * is validated on real phones per the BUILD_PLAN H2 checkpoint.
 */
class TransportContractTest {

    @Test
    fun simulatedScenario_emitsDecodablePacketsFromManyDistinctNodes() {
        val transport = SimulatedTransport(nodeCount = 60, scenarioSeconds = 90)
        val seen = mutableListOf<InboundPacket>()
        val latch = CountDownLatch(1)
        transport.onPacket { inbound ->
            synchronized(seen) { seen.add(inbound); if (seen.size >= 60) latch.countDown() }
        }

        transport.start()
        val completed = latch.await(4, TimeUnit.SECONDS)
        transport.shutdown()

        assertTrue("expected >= 60 packets, got ${seen.size}", completed)
        synchronized(seen) {
            seen.forEach { assertEquals(31, it.bytes.size) }
            val decoded = seen.map { PacketCodec.decode(it.bytes) }
            decoded.forEach { assertEquals(0, it.hopCount) }
            assertTrue(decoded.map { it.deviceId.toHex() }.toSet().size >= 50)
        }
    }

    @Test
    fun simulatedScenario_diagnosticsReportRunning() {
        val transport = SimulatedTransport(nodeCount = 40)
        val received = AtomicInteger()
        transport.onPacket { received.incrementAndGet() }
        var lastDiag: TransportDiagnostics? = null
        transport.onDiagnostics { lastDiag = it }

        transport.start()
        Thread.sleep(600)
        transport.shutdown()

        assertNotNull(lastDiag)
        assertTrue("received nothing", received.get() > 0)
        assertEquals("Simulated", lastDiag!!.kind)
    }

    @Test
    fun tollCurve_isMonotonicAndHitsTheRealMilestones() {
        assertEquals(22.0 / 626, SimulatedTransport.tollFraction(0.0), 1e-6)
        assertEquals(95.0 / 626, SimulatedTransport.tollFraction(0.33), 1e-6)
        assertEquals(469.0 / 626, SimulatedTransport.tollFraction(0.66), 1e-6)
        assertEquals(1.0, SimulatedTransport.tollFraction(1.0), 1e-6)

        var prev = -1.0
        var t = 0.0
        while (t <= 1.0) {
            val f = SimulatedTransport.tollFraction(t)
            assertTrue("toll fraction decreased at t=$t", f >= prev - 1e-9)
            prev = f
            t += 0.02
        }
    }

    @Test
    fun fileTransport_roundTripsPacketsThroughJson() {
        val out = FileTransport()
        val a = ByteArray(31) { it.toByte() }
        val b = ByteArray(31) { (100 - it).toByte() }
        out.advertise(a)
        out.advertise(b)
        val json = out.exportJson()

        val parsed = FileTransport.parse(json)
        assertEquals(2, parsed.size)
        assertTrue(parsed[0].contentEquals(a))
        assertTrue(parsed[1].contentEquals(b))

        // and it replays them on start()
        val replay = FileTransport()
        replay.importJson(json)
        val delivered = mutableListOf<ByteArray>()
        replay.onPacket { delivered.add(it.bytes) }
        replay.start()
        assertEquals(2, delivered.size)
        assertTrue(delivered[0].contentEquals(a))
    }

    @Test
    fun fileTransport_rejectsWrongLengthTokens() {
        val json = """{"v":1,"packets":["aabb","${"cd".repeat(31)}"]}"""
        val parsed = FileTransport.parse(json)
        assertEquals(1, parsed.size) // the 4-char token is dropped, the 62-char one kept
    }

    @Test
    fun advertiseRejectsNon31ByteArrays() {
        val t = SimulatedTransport()
        try {
            t.advertise(ByteArray(30))
            throw AssertionError("expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            // ok
        } finally {
            t.shutdown()
        }
    }
}
