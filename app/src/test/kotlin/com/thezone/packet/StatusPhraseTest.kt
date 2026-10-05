package com.thezone.packet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** PacketCodec's STATUS phrase code (docs/PACKET_SPEC.md "STATUS phrase code"). */
class StatusPhraseTest {

    private fun identity() = DeviceIdentity(ByteArray(DeviceIdentity.KEY_BYTES) { it.toByte() })

    private fun statusPacket(id: DeviceIdentity) = PacketCodec.encode(
        Packet(
            version = Packet.PROTOCOL_VERSION, type = Packet.TYPE_STATUS, deviceId = id.deviceId,
            deltaLat = Packet.NO_FIX, deltaLon = Packet.NO_FIX, status = Status.SAFE.code,
            severity = 3, casualties = 0, timestampMinutes = 100, batteryLevel = 10,
            hopCount = 0, nextExpectedTxSeconds = 10, altDelta = Packet.NO_BAROMETER, altTrend = 0,
        ),
        id,
    )

    @Test
    fun `a packet with no phrase set decodes to null`() {
        val bytes = statusPacket(identity())
        assertNull(PacketCodec.statusPhraseCode(bytes))
        assertNull(StatusPhrases.label(PacketCodec.statusPhraseCode(bytes)))
    }

    @Test
    fun `withStatusPhrase round-trips the code and does not change the packet size`() {
        val bytes = statusPacket(identity())
        val withPhrase = PacketCodec.withStatusPhrase(bytes, 4)
        assertEquals(Packet.SIZE_BYTES, withPhrase.size)
        assertEquals(4, PacketCodec.statusPhraseCode(withPhrase))
        assertEquals("Someone injured", StatusPhrases.label(PacketCodec.statusPhraseCode(withPhrase)))
    }

    @Test
    fun `withStatusPhrase does not mutate the input array`() {
        val bytes = statusPacket(identity())
        val before = bytes.copyOf()
        PacketCodec.withStatusPhrase(bytes, 7)
        org.junit.Assert.assertArrayEquals("relay rule 3 applies here too: never mutate the stored original", before, bytes)
    }

    @Test
    fun `is not readable on a RESOLVE or ALERT packet (reserved means something else there)`() {
        val id = identity()
        val resolve = PacketCodec.buildResolve(
            resolver = id, responderKey = ByteArray(16) { 1 }, resolvedContentId = ByteArray(32) { it.toByte() },
            deltaLat = Packet.NO_FIX, deltaLon = Packet.NO_FIX, batteryLevel = 10, timestampMinutes = 5, nextExpectedTxSeconds = 10,
        )
        assertNull(PacketCodec.statusPhraseCode(resolve))

        val alert = PacketCodec.buildAlert(
            issuer = id, authorityKey = ByteArray(16) { 1 }, category = PacketCodec.ALERT_WARNING, phraseCode = 1,
            deltaLat = Packet.NO_FIX, deltaLon = Packet.NO_FIX, radiusMeters = 100, issuedAtMinutes = 10,
            validForMinutes = 60, batteryLevel = 10,
        )
        assertNull(PacketCodec.statusPhraseCode(alert))
    }

    @Test
    fun `auth is unaffected by withStatusPhrase, since auth covers only 0 until 19`() {
        val id = identity()
        val bytes = statusPacket(id)
        val withPhrase = PacketCodec.withStatusPhrase(bytes, 2)
        assertTrue(PacketCodec.verifyAuth(withPhrase, id))
    }

    @Test
    fun `changing the phrase changes contentId (phrase is part of the message identity)`() {
        val bytes = statusPacket(identity())
        val a = PacketCodec.withStatusPhrase(bytes, 1)
        val b = PacketCodec.withStatusPhrase(bytes, 2)
        assertNotEquals(PacketCodec.contentId(a).toList(), PacketCodec.contentId(b).toList())
    }

    @Test
    fun `rejects 0 (that's the sentinel for no phrase) and out-of-range codes`() {
        val bytes = statusPacket(identity())
        assertThrows(IllegalArgumentException::class.java) { PacketCodec.withStatusPhrase(bytes, 0) }
        assertThrows(IllegalArgumentException::class.java) { PacketCodec.withStatusPhrase(bytes, 256) }
        assertThrows(IllegalArgumentException::class.java) { PacketCodec.withStatusPhrase(bytes, -1) }
    }

    @Test
    fun `StatusPhrases label is null for 0, null input, and out-of-range codes, never throws`() {
        assertNull(StatusPhrases.label(null))
        assertNull(StatusPhrases.label(0))
        assertNull(StatusPhrases.label(999))
        assertEquals("Child with me", StatusPhrases.label(1))
    }
}
