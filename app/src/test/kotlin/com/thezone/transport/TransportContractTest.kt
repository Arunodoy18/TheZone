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
    fun simulatedTransport_emitsDecodablePacketsFromDistinctPeers() {
        val transport = SimulatedTransport(peerCount = 3, periodMillis = 50L)
        val seen = mutableListOf<InboundPacket>()
        val latch = CountDownLatch(9) // 3 peers x 3 rounds
        transport.onPacket { inbound ->
            synchronized(seen) { seen.add(inbound) }
            latch.countDown()
        }

        transport.start()
        val completed = latch.await(3, TimeUnit.SECONDS)
        transport.shutdown()

        assertTrue("expected >= 9 packets, got ${seen.size}", completed)

        seen.forEach { assertEquals(31, it.bytes.size) }
        val decoded = seen.map { PacketCodec.decode(it.bytes) }
        decoded.forEach {
            assertEquals(0, it.hopCount)
            assertTrue(it.nextExpectedTxSeconds in setOf(1, 10, 60, 300))
        }
        val distinctPeers = decoded.map { it.deviceId.toHex() }.toSet()
        assertEquals(3, distinctPeers.size)
    }

    @Test
    fun simulatedTransport_diagnosticsReportRunningAndCounts() {
        val transport = SimulatedTransport(peerCount = 2, periodMillis = 50L)
        val received = AtomicInteger()
        transport.onPacket { received.incrementAndGet() }

        var lastDiag: TransportDiagnostics? = null
        transport.onDiagnostics { lastDiag = it }

        transport.start()
        Thread.sleep(250)
        transport.shutdown()

        assertNotNull(lastDiag)
        assertTrue("received nothing", received.get() > 0)
        assertEquals("Simulated", lastDiag!!.kind)
        assertTrue(lastDiag!!.packetsReceived > 0)
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
