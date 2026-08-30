package com.thezone.packet

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The ten test vectors from `docs/PACKET_SPEC.md`. Written before any Android
 * code path touches the contract. `./gradlew test` must stay green.
 */
class PacketCodecTest {

    private val seededRandom = Random(0xC0FFEE)

    private fun identity(random: Random = seededRandom): DeviceIdentity =
        DeviceIdentity(ByteArray(DeviceIdentity.KEY_BYTES) { random.nextInt().toByte() })

    private fun randomPacket(random: Random, identity: DeviceIdentity): Packet =
        Packet(
            version = Packet.PROTOCOL_VERSION,
            type = Packet.TYPE_STATUS,
            deviceId = identity.deviceId,
            deltaLat = if (random.nextInt(10) == 0) Packet.NO_FIX else random.nextInt(-32767, 32768),
            deltaLon = if (random.nextInt(10) == 0) Packet.NO_FIX else random.nextInt(-32767, 32768),
            status = random.nextInt(0, 7),
            severity = random.nextInt(0, 16),
            casualties = random.nextInt(0, 16),
            timestampMinutes = random.nextInt(0, 65536),
            batteryLevel = random.nextInt(0, 16),
            hopCount = random.nextInt(0, 16),
            nextExpectedTxSeconds = random.nextInt(0, 65536),
            altDelta = if (random.nextInt(8) == 0) Packet.NO_BAROMETER else random.nextInt(-127, 128),
            altTrend = random.nextInt(-128, 128),
        )

    /** 1. Round-trip: encode(p) -> decode -> equals(p) for 1000 randomised packets. */
    @Test
    fun roundTripEquals_for1000RandomPackets() {
        val random = Random(1)
        repeat(1000) {
            val id = identity(random)
            val original = randomPacket(random, id)
            val decoded = PacketCodec.decode(PacketCodec.encode(original, id))
            assertEquals("iteration $it", original, decoded)
        }
    }

    /** 2. Every encode produces exactly 31 bytes, always. */
    @Test
    fun everyEncodeIsExactly31Bytes() {
        val random = Random(2)
        repeat(500) {
            val id = identity(random)
            assertEquals(31, PacketCodec.encode(randomPacket(random, id), id).size)
        }
    }

    /** 3. Position: (origin_lat + 0.001, origin_lon) -> delta_lat == 100. */
    @Test
    fun positionDeltaOfOneMilliDegreeIs100() {
        assertEquals(
            100,
            GeoPosition.encodeDelta(GeoPosition.ORIGIN_LAT + 0.001, GeoPosition.ORIGIN_LAT),
        )
        assertEquals(0, GeoPosition.encodeDelta(GeoPosition.ORIGIN_LON, GeoPosition.ORIGIN_LON))

        // and it survives the full packet round-trip
        val id = identity()
        val p = basePacket(id).copy(deltaLat = 100, deltaLon = 0)
        assertEquals(100, PacketCodec.decode(PacketCodec.encode(p, id)).deltaLat)
    }

    /** 4. No GPS fix survives round-trip as no-fix, not as 0,0. */
    @Test
    fun noFixSurvivesAsNoFix() {
        val id = identity()
        val p = basePacket(id).copy(deltaLat = Packet.NO_FIX, deltaLon = Packet.NO_FIX)
        val decoded = PacketCodec.decode(PacketCodec.encode(p, id))

        assertFalse(decoded.hasFix())
        assertEquals(Packet.NO_FIX, decoded.deltaLat)
        assertEquals(Packet.NO_FIX, decoded.deltaLon)
        assertNotEquals(0, decoded.deltaLat)

        // wire bytes are 0x8000 / 0x8000
        val bytes = PacketCodec.encode(p, id)
        assertEquals(0x80.toByte(), bytes[7])
        assertEquals(0x00.toByte(), bytes[8])
        assertEquals(0x80.toByte(), bytes[9])
        assertEquals(0x00.toByte(), bytes[10])
    }

    /** 5. alt_delta of 0x80 decodes to "no barometer", not to -128 metres. */
    @Test
    fun altDelta0x80IsNoBarometerNotMinus128() {
        val id = identity()
        val p = basePacket(id).copy(altDelta = Packet.NO_BAROMETER)
        val bytes = PacketCodec.encode(p, id)

        assertEquals(0x80.toByte(), bytes[18])

        val decoded = PacketCodec.decode(bytes)
        assertFalse(decoded.hasBarometer())
        assertEquals(Packet.NO_BAROMETER, decoded.altDelta)

        // a real -127 reading is still distinct and round-trips
        val real = PacketCodec.decode(PacketCodec.encode(p.copy(altDelta = -127), id))
        assertTrue(real.hasBarometer())
        assertEquals(-127, real.altDelta)
    }

    /** 6. Battery nibble: 100% -> 15, 0% -> 0, 50% -> 7 or 8. */
    @Test
    fun batteryNibbleMapping() {
        assertEquals(15, BatteryScale.percentToNibble(100))
        assertEquals(0, BatteryScale.percentToNibble(0))
        assertTrue(BatteryScale.percentToNibble(50) in setOf(7, 8))
    }

    /** 7. Hop increment on relay does not mutate the stored original. */
    @Test
    fun hopIncrementDoesNotMutateOriginal() {
        val id = identity()
        val stored = PacketCodec.encode(basePacket(id).copy(hopCount = 0, batteryLevel = 12), id)
        val snapshot = stored.copyOf()

        val relayed = PacketCodec.incrementHop(stored)

        assertArrayEquals("stored original must be untouched", snapshot, stored)
        assertEquals(0, PacketCodec.hopCount(stored))
        assertEquals(1, PacketCodec.hopCount(relayed))
        // battery nibble in the same byte is preserved
        assertEquals(12, PacketCodec.decode(relayed).batteryLevel)

        // ceiling: never exceeds 15
        var b = PacketCodec.encode(basePacket(id).copy(hopCount = 15), id)
        b = PacketCodec.incrementHop(b)
        assertEquals(15, PacketCodec.hopCount(b))
    }

    /**
     * 8. Two packets differing only in hop_count.
     *
     * Deliberate decision (PACKET_SPEC test-8 note): identity is the message, not
     * the journey. Raw bytes and the journey-sensitive hash differ; the content
     * identity used for dedup does NOT — so a report doesn't re-enter the store at
     * every hop.
     */
    @Test
    fun hopCountChangesRawHashButNotContentId() {
        val id = identity()
        val base = basePacket(id)
        val atHop2 = PacketCodec.encode(base.withHop(2), id)
        val atHop7 = PacketCodec.encode(base.withHop(7), id)

        assertFalse("raw bytes differ", atHop2.contentEquals(atHop7))
        assertFalse(
            "journey-sensitive hash differs",
            PacketCodec.rawHash(atHop2).contentEquals(PacketCodec.rawHash(atHop7)),
        )
        assertTrue(
            "content identity is hop-independent",
            PacketCodec.contentId(atHop2).contentEquals(PacketCodec.contentId(atHop7)),
        )

        // sanity: a real payload change DOES move the content id
        val different = PacketCodec.encode(base.copy(severity = base.severity xor 1), id)
        assertFalse(PacketCodec.contentId(atHop2).contentEquals(PacketCodec.contentId(different)))
    }

    /** 9. auth changes when any payload byte changes. */
    @Test
    fun authChangesWhenAnyPayloadByteChanges() {
        val id = identity()
        val bytes = PacketCodec.encode(basePacket(id), id)
        val baselineAuth = PacketCodec.authBytes(bytes)

        // payload = bytes [0, 19); auth signs exactly that range
        for (i in 0 until 19) {
            val mutated = bytes.copyOf()
            mutated[i] = (mutated[i].toInt() xor 0x01).toByte()
            val newAuth = DeviceIdentity.sha256(id.key, mutated.copyOfRange(0, 19)).copyOf(4)
            assertFalse("flipping byte $i left auth unchanged", newAuth.contentEquals(baselineAuth))
        }

        // and the positive path: verifyAuth accepts a correctly signed packet
        assertTrue(PacketCodec.verifyAuth(bytes, id))
        // a forged packet (wrong key) fails
        assertFalse(PacketCodec.verifyAuth(bytes, identity(Random(999))))
    }

    /** 10. next_expected_tx round-trips at all four ladder values. */
    @Test
    fun nextExpectedTxRoundTripsAtLadderValues() {
        val id = identity()
        for (seconds in listOf(1, 10, 60, 300)) {
            val decoded = PacketCodec.decode(
                PacketCodec.encode(basePacket(id).copy(nextExpectedTxSeconds = seconds), id),
            )
            assertEquals(seconds, decoded.nextExpectedTxSeconds)
        }
    }

    // --- helpers ---------------------------------------------------------

    private fun basePacket(id: DeviceIdentity) = Packet(
        version = Packet.PROTOCOL_VERSION,
        type = Packet.TYPE_STATUS,
        deviceId = id.deviceId,
        deltaLat = 1234,
        deltaLon = -5678,
        status = Status.TRAPPED_DEBRIS.code,
        severity = 9,
        casualties = 3,
        timestampMinutes = 40_000,
        batteryLevel = 7,
        hopCount = 0,
        nextExpectedTxSeconds = 10,
        altDelta = -4,
        altTrend = 2,
    )
}
