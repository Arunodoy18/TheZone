package com.thezone.packet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** PacketCodec's TYPE_SIG fragment layout — see PacketCodec's own doc comment for the byte map. */
class PacketCodecSigTest {

    private val random = Random(42)
    private fun deviceId() = ByteArray(Packet.DEVICE_ID_BYTES) { random.nextInt().toByte() }
    private fun contentId32() = ByteArray(32) { random.nextInt().toByte() }
    private fun chunk16() = ByteArray(PacketCodec.SIG_FRAGMENT_BYTES) { random.nextInt().toByte() }

    @Test
    fun `every fragment is exactly 31 bytes`() {
        val bytes = PacketCodec.buildSigFragment(deviceId(), PacketCodec.TYPE_RESOLVE, contentId32(), 0, chunk16())
        assertEquals(Packet.SIZE_BYTES, bytes.size)
    }

    @Test
    fun `isSig is true only for a SIG-typed packet`() {
        val sig = PacketCodec.buildSigFragment(deviceId(), PacketCodec.TYPE_ALERT, contentId32(), 1, chunk16())
        assertTrue(PacketCodec.isSig(sig))

        val identity = DeviceIdentity(ByteArray(DeviceIdentity.KEY_BYTES))
        val status = PacketCodec.encode(
            Packet(
                version = Packet.PROTOCOL_VERSION, type = 0, deviceId = identity.deviceId,
                deltaLat = Packet.NO_FIX, deltaLon = Packet.NO_FIX, status = 0, severity = 0, casualties = 0,
                timestampMinutes = 0, batteryLevel = 15, hopCount = 0, nextExpectedTxSeconds = 1,
                altDelta = Packet.NO_BAROMETER, altTrend = 0,
            ),
            identity,
        )
        assertFalse(PacketCodec.isSig(status))
    }

    @Test
    fun `decode round-trips signer, target type, fragment index, prefix and chunk`() {
        val signer = deviceId()
        val cid = contentId32()
        val chunk = chunk16()
        val bytes = PacketCodec.buildSigFragment(signer, PacketCodec.TYPE_ALERT, cid, 2, chunk)
        val f = PacketCodec.decodeSigFragment(bytes)!!
        assertEquals(signer.joinToString("") { "%02x".format(it) }, f.signerDeviceIdHex)
        assertEquals(PacketCodec.TYPE_ALERT, f.targetType)
        assertEquals(2, f.fragmentIndex)
        assertEquals(
            cid.copyOfRange(0, PacketCodec.SIG_TARGET_PREFIX_BYTES).joinToString("") { "%02x".format(it) },
            f.targetContentIdPrefixHex,
        )
        org.junit.Assert.assertArrayEquals(chunk, f.chunk)
    }

    @Test
    fun `four fragments reassemble to the original 64-byte signature`() {
        val signer = deviceId()
        val cid = contentId32()
        val signature = ByteArray(64) { random.nextInt().toByte() }
        val reassembled = ByteArray(64)
        for (i in 0 until PacketCodec.SIG_FRAGMENT_COUNT) {
            val chunk = signature.copyOfRange(i * 16, (i + 1) * 16)
            val bytes = PacketCodec.buildSigFragment(signer, PacketCodec.TYPE_RESOLVE, cid, i, chunk)
            val f = PacketCodec.decodeSigFragment(bytes)!!
            f.chunk.copyInto(reassembled, i * 16)
        }
        org.junit.Assert.assertArrayEquals(signature, reassembled)
    }

    @Test
    fun `relay hop increment does not corrupt the signature chunk`() {
        val signer = deviceId()
        val cid = contentId32()
        val chunk = chunk16()
        val bytes = PacketCodec.buildSigFragment(signer, PacketCodec.TYPE_RESOLVE, cid, 0, chunk)
        val relayed = PacketCodec.incrementHop(bytes)
        val f = PacketCodec.decodeSigFragment(relayed)!!
        org.junit.Assert.assertArrayEquals("hop increment must not touch the chunk bytes", chunk, f.chunk)
        assertEquals(1, PacketCodec.hopCount(relayed))
    }

    @Test
    fun `contentId differs per fragment index so all four coexist in a content-addressed store`() {
        val signer = deviceId()
        val cid = contentId32()
        val chunk = chunk16()
        val ids = (0 until PacketCodec.SIG_FRAGMENT_COUNT).map {
            PacketCodec.contentId(PacketCodec.buildSigFragment(signer, PacketCodec.TYPE_RESOLVE, cid, it, chunk)).toList()
        }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `authShapeValid passes for every fragment (meta byte is always nonzero)`() {
        val bytes = PacketCodec.buildSigFragment(deviceId(), PacketCodec.TYPE_RESOLVE, contentId32(), 3, chunk16())
        assertTrue(PacketCodec.authShapeValid(bytes))
    }

    @Test
    fun `decodeSigFragment returns null for a non-SIG packet`() {
        val resolve = PacketCodec.buildResolve(
            resolver = DeviceIdentity(ByteArray(DeviceIdentity.KEY_BYTES) { it.toByte() }),
            resolvedContentId = contentId32(),
            deltaLat = Packet.NO_FIX, deltaLon = Packet.NO_FIX,
            batteryLevel = 10, timestampMinutes = 0, nextExpectedTxSeconds = 10,
        )
        assertNull(PacketCodec.decodeSigFragment(resolve))
    }

    @Test
    fun `rejects an out-of-range fragment index or wrong-size chunk`() {
        assertThrows { PacketCodec.buildSigFragment(deviceId(), PacketCodec.TYPE_RESOLVE, contentId32(), 4, chunk16()) }
        assertThrows { PacketCodec.buildSigFragment(deviceId(), PacketCodec.TYPE_RESOLVE, contentId32(), 0, ByteArray(15)) }
        assertThrows { PacketCodec.buildSigFragment(deviceId(), 0, contentId32(), 0, chunk16()) } // targetType must be RESOLVE/ALERT
    }

    private fun assertThrows(block: () -> Unit) {
        var threw = false
        try { block() } catch (e: IllegalArgumentException) { threw = true }
        assertTrue("expected IllegalArgumentException", threw)
    }
}
