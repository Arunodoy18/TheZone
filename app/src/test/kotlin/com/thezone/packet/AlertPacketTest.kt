package com.thezone.packet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** ALERT (packet type 2) — signed with the authority key, floods the mesh. */
class AlertPacketTest {

    private fun id(seed: Int) =
        DeviceIdentity(ByteArray(DeviceIdentity.KEY_BYTES) { Random(seed).nextInt().toByte() })

    @Test
    fun `alert round-trips its fields and verifies with the authority key only`() {
        val issuer = id(1)
        val key = ByteArray(16) { (it * 3 + 5).toByte() }
        val bytes = PacketCodec.buildAlert(
            issuer = issuer, authorityKey = key,
            category = PacketCodec.ALERT_EXTREME, phraseCode = 2, // "MOVE TO HIGH GROUND"
            deltaLat = 1200, deltaLon = -800,
            radiusMeters = 800, issuedAtMinutes = 40_000, validForMinutes = 360,
            batteryLevel = 11,
        )
        assertEquals(Packet.SIZE_BYTES, bytes.size)
        assertTrue(PacketCodec.isAlert(bytes))
        assertFalse(PacketCodec.isResolve(bytes))

        assertTrue(PacketCodec.verifyAuthWithKey(bytes, key))
        assertFalse(PacketCodec.verifyAuthWithKey(bytes, ByteArray(16)))

        val a = PacketCodec.decodeAlert(bytes)
        assertEquals(PacketCodec.ALERT_EXTREME, a.category)
        assertEquals(2, a.phraseCode)
        assertEquals(1200, a.deltaLat)
        assertEquals(-800, a.deltaLon)
        assertEquals(800, a.radiusMeters)           // 20 m units -> 40 -> back to 800
        assertEquals(40_000, a.issuedAtMinutes)
        assertEquals(360, a.validForMinutes)
        assertEquals(0, a.hopCount)
        assertEquals(issuer.deviceId.joinToString("") { "%02x".format(it) }, a.issuerHex)
    }

    @Test
    fun `two alerts differing only in phrase have different content ids`() {
        val issuer = id(2); val key = ByteArray(16) { 7 }
        fun mk(phrase: Int) = PacketCodec.buildAlert(
            issuer, key, PacketCodec.ALERT_WARNING, phrase,
            0, 0, 500, 40_000, 120, 8,
        )
        assertNotEquals(
            PacketCodec.contentId(mk(1)).toList(),
            PacketCodec.contentId(mk(5)).toList(),
        )
    }

    @Test
    fun `phrase table renders label and full text and category names`() {
        assertEquals("MOVE TO HIGH GROUND", AlertText.label(2))
        assertTrue(AlertText.full(1).startsWith("Evacuate"))
        assertEquals("EXTREME", AlertText.categoryName(4))
        assertEquals("ALERT", AlertText.label(999)) // out of range -> entry 0
    }
}
