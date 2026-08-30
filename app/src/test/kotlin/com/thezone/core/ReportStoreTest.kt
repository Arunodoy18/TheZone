package com.thezone.core

import com.thezone.packet.DeviceIdentity
import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ReportStoreTest {

    private val rng = Random(0x5A5A)

    private fun identity(): DeviceIdentity =
        DeviceIdentity(ByteArray(DeviceIdentity.KEY_BYTES) { rng.nextInt().toByte() })

    private fun packetBytes(
        id: DeviceIdentity = identity(),
        hop: Int = 0,
        status: Int = 2,
        battery: Int = 8,
        nextTx: Int = 10,
    ): ByteArray = PacketCodec.encode(
        Packet(
            version = Packet.PROTOCOL_VERSION,
            type = Packet.TYPE_STATUS,
            deviceId = id.deviceId,
            deltaLat = 10,
            deltaLon = -20,
            status = status,
            severity = 5,
            casualties = 1,
            timestampMinutes = 1000,
            batteryLevel = battery,
            hopCount = hop,
            nextExpectedTxSeconds = nextTx,
            altDelta = 3,
            altTrend = 0,
        ),
        id,
    )

    @Test
    fun acceptNew_thenDuplicate() {
        val store = ReportStore()
        val bytes = packetBytes()

        assertEquals(AcceptOutcome.NEW, store.accept(bytes, rssiDbm = -50))
        assertEquals(1, store.size)

        assertEquals(AcceptOutcome.DUPLICATE, store.accept(bytes, rssiDbm = -70))
        assertEquals(1, store.size)

        val report = store.all().single()
        assertEquals(2, report.timesHeard)
        assertEquals(-50, report.bestRssiDbm) // strongest kept
    }

    @Test
    fun sameMessageAtHigherHop_isDuplicate_keepsLowerHop() {
        val store = ReportStore()
        val id = identity()
        val hop1 = packetBytes(id, hop = 1)
        val hop4 = packetBytes(id, hop = 4)

        assertEquals(AcceptOutcome.NEW, store.accept(hop1, -55))
        // contentId ignores the hop nibble, so this is the same identity
        assertEquals(AcceptOutcome.DUPLICATE, store.accept(hop4, -55))
        assertEquals(1, store.size)
        assertEquals(1, store.all().single().receivedHopCount)
    }

    @Test
    fun lowerHopArrivingLater_replacesStoredBytes() {
        val store = ReportStore()
        val id = identity()
        assertEquals(AcceptOutcome.NEW, store.accept(packetBytes(id, hop = 5), -55))
        assertEquals(AcceptOutcome.UPDATED, store.accept(packetBytes(id, hop = 2), -55))
        assertEquals(2, store.all().single().receivedHopCount)
    }

    @Test
    fun rejectsMalformedAndBadAuthShape() {
        val store = ReportStore()
        assertEquals(AcceptOutcome.REJECTED_MALFORMED, store.accept(ByteArray(30), -50))

        val zeroedAuth = packetBytes().also { for (i in 19..22) it[i] = 0 }
        assertEquals(AcceptOutcome.REJECTED_AUTH_SHAPE, store.accept(zeroedAuth, -50))
        assertEquals(0, store.size)
    }

    @Test
    fun relayBatch_incrementsCopy_leavesStoredOriginalUntouched() {
        val store = ReportStore()
        val bytes = packetBytes(hop = 0)
        store.accept(bytes, -50)
        val storedBefore = store.all().single().bytes.copyOf()

        val relay = store.relayBatch(10)
        assertEquals(1, relay.size)
        assertEquals(1, PacketCodec.hopCount(relay[0]))
        assertEquals(0, PacketCodec.hopCount(store.all().single().bytes))
        assertArrayEquals(storedBefore, store.all().single().bytes)
    }

    @Test
    fun relayBatch_excludesOwnDeviceAndHopCeiling() {
        val store = ReportStore()
        val me = identity()
        store.ownDeviceIdHex = me.deviceId.joinToString("") { "%02x".format(it) }

        store.accept(packetBytes(me, hop = 0), -40)          // own — never relayed
        store.accept(packetBytes(identity(), hop = 15), -60) // ceiling — dropped from relay
        store.accept(packetBytes(identity(), hop = 3), -60)  // the only relayable one

        val relay = store.relayBatch(10)
        assertEquals(1, relay.size)
        assertEquals(4, PacketCodec.hopCount(relay[0]))
    }

    @Test
    fun relayBatch_roundRobinsAcrossCalls() {
        val store = ReportStore()
        repeat(3) { store.accept(packetBytes(identity(), hop = 1), -55) }

        val seen = buildList {
            repeat(3) { add(PacketCodec.decode(store.relayBatch(1).single()).deviceId.joinToString("") { b -> "%02x".format(b) }) }
        }
        assertEquals("each carried report aired once before repeating", 3, seen.toSet().size)
        // 4th call wraps back to the first
        val fourth = PacketCodec.decode(store.relayBatch(1).single()).deviceId.joinToString("") { "%02x".format(it) }
        assertEquals(seen[0], fourth)
    }

    @Test
    fun mergeFrom_isSetUnion_andIdempotent() {
        val a = ReportStore()
        val b = ReportStore()
        val shared = packetBytes()
        val onlyA = packetBytes()
        val onlyB = packetBytes()

        a.accept(shared, -50); a.accept(onlyA, -50)
        b.accept(shared, -50); b.accept(onlyB, -50)

        assertEquals(1, a.mergeFrom(b.snapshotRaw())) // only onlyB is new to A
        assertEquals(3, a.size)
        assertEquals(0, a.mergeFrom(b.snapshotRaw())) // idempotent
        assertEquals(3, a.size)
    }

    @Test
    fun capEvictsLeastRecentlyHeard_neverOwn() {
        var clock = 0L
        val store = ReportStore(maxReports = 5, nowMillis = { clock })
        val me = identity()
        store.ownDeviceIdHex = me.deviceId.joinToString("") { "%02x".format(it) }
        store.accept(packetBytes(me), rssiDbm = 0, receivedAtMillis = clock)

        val ids = (0 until 10).map { identity() }
        ids.forEach { clock += 1000; store.accept(packetBytes(it), -50, clock) }

        assertEquals(5, store.size)
        // own survived; the survivors are the most recently heard peers
        assertTrue(store.all().any { it.isOwn })
        val survivorDevs = store.all().filterNot { it.isOwn }
            .map { it.packet.deviceId.joinToString("") { b -> "%02x".format(b) } }.toSet()
        val newestFour = ids.takeLast(4).map { it.deviceId.joinToString("") { b -> "%02x".format(b) } }
        assertTrue(survivorDevs.containsAll(newestFour))
    }

    @Test
    fun identicalRebroadcastDedups() {
        val store = ReportStore()
        val bytes = packetBytes(hop = 0)
        repeat(20) { store.accept(bytes.copyOf(), -50) }
        assertEquals(1, store.size)
        assertEquals(20, store.all().single().timesHeard)
    }

    /**
     * BUILD_PLAN H3 checkpoint, in miniature: A is out of range of C, B carries
     * the packet A -> C. C ends up holding A's report, reached via B, at hop 2.
     */
    @Test
    fun storeCarryForward_endToEnd_phoneCHoldsAtHopTwo() {
        val a = identity()
        val storeB = ReportStore().apply { ownDeviceIdHex = null }
        val storeC = ReportStore()

        // A broadcasts hop 0. B hears it directly.
        val fromA = packetBytes(a, hop = 0)
        assertEquals(AcceptOutcome.NEW, storeB.accept(fromA, rssiDbm = -60))

        // B relays; the carried copy is hop 1.
        val bRelay = storeB.relayBatch(4).single()
        assertEquals(1, PacketCodec.hopCount(bRelay))

        // C is out of range of A and only hears B's relay.
        assertEquals(AcceptOutcome.NEW, storeC.accept(bRelay, rssiDbm = -65))

        val atC = storeC.all().single()
        assertArrayEquals(a.deviceId, atC.packet.deviceId)
        assertEquals(1, atC.receivedHopCount)
        assertEquals(2, atC.hopsFromOrigin)                       // what C shows / would relay as
        assertEquals(2, PacketCodec.hopCount(storeC.relayBatch(1).single()))
        assertFalse(atC.isOwn)
    }
}
