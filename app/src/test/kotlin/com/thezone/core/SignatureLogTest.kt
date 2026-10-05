package com.thezone.core

import com.thezone.identity.EcdsaSignature
import com.thezone.identity.RosterEntry
import com.thezone.identity.TrustRoster
import com.thezone.packet.DeviceIdentity
import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

class SignatureLogTest {

    private fun keyPair(): KeyPair =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private fun rosterOf(deviceIdHex: String, kp: KeyPair): TrustRoster {
        val pubHex = EcdsaSignature.encodePublicKey(kp.public as ECPublicKey).joinToString("") { "%02x".format(it) }
        return TrustRoster(listOf(RosterEntry(deviceIdHex, pubHex)))
    }

    // DeviceIdentity's deviceId is derived from its key, not settable directly, so
    // every test reads the signer's device_id back off the *built packet*, which
    // is what SignatureLog actually keys signing on.
    private fun resolvePacket(): ByteArray = PacketCodec.buildResolve(
        resolver = DeviceIdentity(ByteArray(DeviceIdentity.KEY_BYTES) { 9 }),
        responderKey = ByteArray(16) { 1 },
        resolvedContentId = ByteArray(32) { it.toByte() },
        deltaLat = Packet.NO_FIX, deltaLon = Packet.NO_FIX,
        batteryLevel = 10, timestampMinutes = 5, nextExpectedTxSeconds = 10,
    )

    private fun fragmentsFor(signerId: ByteArray, targetType: Int, targetBytes: ByteArray, privateKey: java.security.PrivateKey): List<ByteArray> {
        val digest = PacketCodec.contentId(targetBytes)
        val sig = EcdsaSignature.sign(privateKey, digest)
        return (0 until PacketCodec.SIG_FRAGMENT_COUNT).map { i ->
            val chunk = sig.copyOfRange(i * PacketCodec.SIG_FRAGMENT_BYTES, (i + 1) * PacketCodec.SIG_FRAGMENT_BYTES)
            PacketCodec.buildSigFragment(signerId, targetType, digest, i, chunk)
        }
    }

    @Test
    fun `verifies once all four fragments and the target have arrived`() {
        val kp = keyPair()
        val target = resolvePacket()
        val signerId = PacketCodec.decode(target).deviceId
        val signerHex = signerId.joinToString("") { "%02x".format(it) }
        val roster = rosterOf(signerHex, kp)

        val log = SignatureLog()
        fragmentsFor(signerId, PacketCodec.TYPE_RESOLVE, target, kp.private).forEach { log.ingest(it) }

        assertEquals(0, log.verifiedCount) // sweep hasn't run yet
        log.sweep(roster) { _, type -> if (type == PacketCodec.TYPE_RESOLVE) target else null }

        val contentIdHex = PacketCodec.contentId(target).joinToString("") { "%02x".format(it) }
        assertTrue(log.isVerified(contentIdHex))
        assertEquals(signerHex, log.verifiedSigner(contentIdHex))
        assertEquals(1, log.verifiedCount)
    }

    @Test
    fun `does not verify until the target packet is known`() {
        val kp = keyPair()
        val target = resolvePacket()
        val signerId = PacketCodec.decode(target).deviceId
        val signerHex = signerId.joinToString("") { "%02x".format(it) }
        val roster = rosterOf(signerHex, kp)

        val log = SignatureLog()
        fragmentsFor(signerId, PacketCodec.TYPE_RESOLVE, target, kp.private).forEach { log.ingest(it) }
        log.sweep(roster) { _, _ -> null } // target not seen yet
        assertEquals(0, log.verifiedCount)
        assertEquals(1, log.pendingCount)

        log.sweep(roster) { _, type -> if (type == PacketCodec.TYPE_RESOLVE) target else null } // arrives now
        assertEquals(1, log.verifiedCount)
        assertEquals(0, log.pendingCount)
    }

    @Test
    fun `fragments can arrive in any order`() {
        val kp = keyPair()
        val target = resolvePacket()
        val signerId = PacketCodec.decode(target).deviceId
        val signerHex = signerId.joinToString("") { "%02x".format(it) }
        val roster = rosterOf(signerHex, kp)

        val log = SignatureLog()
        fragmentsFor(signerId, PacketCodec.TYPE_RESOLVE, target, kp.private).shuffled(kotlin.random.Random(3)).forEach { log.ingest(it) }
        log.sweep(roster) { _, type -> if (type == PacketCodec.TYPE_RESOLVE) target else null }

        val contentIdHex = PacketCodec.contentId(target).joinToString("") { "%02x".format(it) }
        assertTrue(log.isVerified(contentIdHex))
    }

    @Test
    fun `an unprovisioned signer never verifies`() {
        val kp = keyPair()
        val target = resolvePacket()
        val signerId = PacketCodec.decode(target).deviceId

        val log = SignatureLog()
        fragmentsFor(signerId, PacketCodec.TYPE_RESOLVE, target, kp.private).forEach { log.ingest(it) }
        log.sweep(TrustRoster(emptyList())) { _, type -> if (type == PacketCodec.TYPE_RESOLVE) target else null }

        val contentIdHex = PacketCodec.contentId(target).joinToString("") { "%02x".format(it) }
        assertFalse(log.isVerified(contentIdHex))
        assertNull(log.verifiedSigner(contentIdHex))
    }

    @Test
    fun `a forged signature from a different key never verifies`() {
        val realKey = keyPair()
        val forgerKey = keyPair()
        val target = resolvePacket()
        val signerId = PacketCodec.decode(target).deviceId
        val signerHex = signerId.joinToString("") { "%02x".format(it) }
        val roster = rosterOf(signerHex, realKey) // roster trusts realKey's public half

        val log = SignatureLog()
        fragmentsFor(signerId, PacketCodec.TYPE_RESOLVE, target, forgerKey.private).forEach { log.ingest(it) } // signed with the WRONG key
        log.sweep(roster) { _, type -> if (type == PacketCodec.TYPE_RESOLVE) target else null }

        val contentIdHex = PacketCodec.contentId(target).joinToString("") { "%02x".format(it) }
        assertFalse(log.isVerified(contentIdHex))
    }

    @Test
    fun `an incomplete fragment set never verifies`() {
        val kp = keyPair()
        val target = resolvePacket()
        val signerId = PacketCodec.decode(target).deviceId
        val signerHex = signerId.joinToString("") { "%02x".format(it) }
        val roster = rosterOf(signerHex, kp)

        val log = SignatureLog()
        fragmentsFor(signerId, PacketCodec.TYPE_RESOLVE, target, kp.private).dropLast(1).forEach { log.ingest(it) } // only 3 of 4
        log.sweep(roster) { _, type -> if (type == PacketCodec.TYPE_RESOLVE) target else null }

        assertEquals(0, log.verifiedCount)
        assertEquals(1, log.pendingCount) // the 3 fragments sit in pending, incomplete — never swept
    }

    @Test
    fun `ingesting a non-SIG packet is ignored, not a crash`() {
        val log = SignatureLog()
        val notSig = resolvePacket()
        log.ingest(notSig)
        assertEquals(0, log.pendingCount)
    }
}
