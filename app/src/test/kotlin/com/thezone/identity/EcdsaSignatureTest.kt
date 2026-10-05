package com.thezone.identity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import kotlin.random.Random

/**
 * Runs entirely on the desktop JVM (SunEC) — the exact DER<->raw conversion
 * and sign/verify path a real Android phone will exercise via AndroidKeyStore,
 * just with a software-generated key here.
 */
class EcdsaSignatureTest {

    private fun keyPair() =
        KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    @Test
    fun `sign then verify round-trips for many random messages`() {
        val kp = keyPair()
        val pub = EcdsaSignature.encodePublicKey(kp.public as ECPublicKey)
        val random = Random(1)
        repeat(50) { i ->
            val message = ByteArray(32) { random.nextInt().toByte() }
            val sig = EcdsaSignature.sign(kp.private, message)
            org.junit.Assert.assertEquals(
                "iteration $i: raw signature must be fixed-width",
                EcdsaSignature.RAW_SIGNATURE_BYTES,
                sig.size,
            )
            assertTrue("iteration $i: signature must verify", EcdsaSignature.verify(pub, message, sig))
        }
    }

    @Test
    fun `verify fails for a tampered message`() {
        val kp = keyPair()
        val pub = EcdsaSignature.encodePublicKey(kp.public as ECPublicKey)
        val message = ByteArray(32) { it.toByte() }
        val sig = EcdsaSignature.sign(kp.private, message)
        val tampered = message.copyOf().also { it[0] = (it[0] + 1).toByte() }
        assertFalse(EcdsaSignature.verify(pub, tampered, sig))
    }

    @Test
    fun `verify fails for a tampered signature`() {
        val kp = keyPair()
        val pub = EcdsaSignature.encodePublicKey(kp.public as ECPublicKey)
        val message = ByteArray(32) { it.toByte() }
        val sig = EcdsaSignature.sign(kp.private, message)
        val tampered = sig.copyOf().also { it[40] = (it[40] + 1).toByte() }
        assertFalse(EcdsaSignature.verify(pub, message, tampered))
    }

    @Test
    fun `verify fails against the wrong public key`() {
        val kp1 = keyPair()
        val kp2 = keyPair()
        val pub2 = EcdsaSignature.encodePublicKey(kp2.public as ECPublicKey)
        val message = ByteArray(32) { it.toByte() }
        val sig = EcdsaSignature.sign(kp1.private, message)
        assertFalse(EcdsaSignature.verify(pub2, message, sig))
    }

    @Test
    fun `verify rejects malformed input without throwing`() {
        val kp = keyPair()
        val pub = EcdsaSignature.encodePublicKey(kp.public as ECPublicKey)
        assertFalse(EcdsaSignature.verify(pub, ByteArray(32), ByteArray(10))) // wrong signature length
        assertFalse(EcdsaSignature.verify(ByteArray(65), ByteArray(32), ByteArray(64))) // garbage pubkey, right length
        assertFalse(EcdsaSignature.verify(ByteArray(3), ByteArray(32), ByteArray(64))) // wrong pubkey length
    }

    @Test
    fun `decodePublicKey rejects the wrong length or prefix`() {
        assertNull(EcdsaSignature.decodePublicKey(ByteArray(64))) // one byte short
        val bad = ByteArray(65).also { it[0] = 0x03 } // not the uncompressed-point prefix
        assertNull(EcdsaSignature.decodePublicKey(bad))
    }

    @Test
    fun `encodePublicKey then decodePublicKey round-trips and still verifies`() {
        val kp = keyPair()
        val raw = EcdsaSignature.encodePublicKey(kp.public as ECPublicKey)
        val decoded = EcdsaSignature.decodePublicKey(raw)
        assertTrue(decoded != null)
        val message = ByteArray(32) { it.toByte() }
        val sig = EcdsaSignature.sign(kp.private, message)
        assertTrue(EcdsaSignature.verify(raw, message, sig))
    }

    @Test
    fun `two signatures over the same message differ (ECDSA nonce is random)`() {
        val kp = keyPair()
        val message = ByteArray(32) { it.toByte() }
        val sig1 = EcdsaSignature.sign(kp.private, message)
        val sig2 = EcdsaSignature.sign(kp.private, message)
        assertNotEquals(sig1.toList(), sig2.toList())
    }
}
