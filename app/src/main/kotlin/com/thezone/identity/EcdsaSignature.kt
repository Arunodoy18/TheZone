package com.thezone.identity

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec

/**
 * ECDSA P-256 sign/verify plus the wire-format conversions Zone needs, built
 * entirely on `java.security` — CLAUDE.md "no crypto library". Pure JVM (no
 * Android import), so it's unit-tested directly on the desktop JDK.
 *
 * Two format decisions, both driven by the 31-byte packet:
 *  - A public key travels as a raw uncompressed EC point (`0x04 || X[32] ||
 *    Y[32]`, 65 bytes) — smaller than an X.509 SubjectPublicKeyInfo and all
 *    [com.thezone.identity.TrustRoster] needs to reconstruct a usable key.
 *  - A signature travels as raw fixed-width `r || s` (32 + 32 = 64 bytes),
 *    not the variable-length ASN.1 DER `Signature` produces — fixed width is
 *    what makes splitting it into four equal 16-byte SIG-packet fragments
 *    (docs/PACKET_SPEC.md "SIG") possible at all.
 *
 * Algorithm is always "SHA256withECDSA" (never "NONEwithECDSA"): some
 * hardware-backed Android Keystore implementations refuse the NONE digest by
 * policy, and SHA256withECDSA is universally supported by both AndroidKeyStore
 * and the desktop JVM's SunEC. We sign [com.thezone.packet.PacketCodec.contentId] —
 * itself already a SHA-256 digest — as the "message", so the underlying
 * algorithm hashes it a second time. That's a deliberate, harmless choice for
 * portability, not a mistake: hashing a digest again does not weaken it.
 */
object EcdsaSignature {

    private const val CURVE = "secp256r1" // NIST P-256, the ubiquitous choice
    private const val COORD_BYTES = 32
    const val RAW_SIGNATURE_BYTES = COORD_BYTES * 2
    const val RAW_PUBLIC_KEY_BYTES = 1 + COORD_BYTES * 2

    /** Sign [message] with [privateKey]; returns raw fixed-width 64-byte `r || s`. */
    fun sign(privateKey: PrivateKey, message: ByteArray): ByteArray {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(message)
        return derToRaw(sig.sign())
    }

    /**
     * Verify [rawSignature] (64 bytes) over [message] against [rawPublicKey]
     * (65-byte uncompressed point). False — never throws — on anything
     * malformed; this sees untrusted mesh data, so no exception should ever
     * escape it.
     */
    fun verify(rawPublicKey: ByteArray, message: ByteArray, rawSignature: ByteArray): Boolean {
        if (rawSignature.size != RAW_SIGNATURE_BYTES) return false
        val pub = decodePublicKey(rawPublicKey) ?: return false
        return runCatching {
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(pub)
            sig.update(message)
            sig.verify(rawToDer(rawSignature))
        }.getOrDefault(false)
    }

    /** `0x04 || X[32] || Y[32]` -> a usable [PublicKey], or null if malformed. */
    fun decodePublicKey(rawPoint: ByteArray): PublicKey? {
        if (rawPoint.size != RAW_PUBLIC_KEY_BYTES || rawPoint[0] != 0x04.toByte()) return null
        return runCatching {
            val x = BigInteger(1, rawPoint.copyOfRange(1, 1 + COORD_BYTES))
            val y = BigInteger(1, rawPoint.copyOfRange(1 + COORD_BYTES, 1 + 2 * COORD_BYTES))
            val params = AlgorithmParameters.getInstance("EC").apply { init(ECGenParameterSpec(CURVE)) }
            val ecParams = params.getParameterSpec(ECParameterSpec::class.java)
            KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), ecParams))
        }.getOrNull()
    }

    /** A public key -> the raw uncompressed-point wire format. */
    fun encodePublicKey(publicKey: ECPublicKey): ByteArray {
        val out = ByteArray(RAW_PUBLIC_KEY_BYTES)
        out[0] = 0x04
        unsignedFixed(publicKey.w.affineX, COORD_BYTES).copyInto(out, 1)
        unsignedFixed(publicKey.w.affineY, COORD_BYTES).copyInto(out, 1 + COORD_BYTES)
        return out
    }

    // --- ASN.1 DER <-> fixed-width raw ---------------------------------
    // Signature.sign() / verify() only speak DER SEQUENCE{INTEGER r, INTEGER s}.
    // A P-256 signature's DER encoding is always short enough for a single
    // length byte (worst case: two 33-byte padded INTEGERs, ~72 bytes total,
    // far under the 128-byte short-form ceiling) — this deliberately does not
    // implement long-form BER lengths, and rejects anything that would need one.

    private fun rawToDer(raw: ByteArray): ByteArray {
        val r = derInteger(raw.copyOfRange(0, COORD_BYTES))
        val s = derInteger(raw.copyOfRange(COORD_BYTES, RAW_SIGNATURE_BYTES))
        val body = r + s
        require(body.size < 0x80) { "unexpectedly long DER body" }
        return byteArrayOf(0x30, body.size.toByte()) + body
    }

    private fun derInteger(coord: ByteArray): ByteArray {
        var start = 0
        while (start < coord.size - 1 && coord[start] == 0.toByte()) start++
        val trimmed = coord.copyOfRange(start, coord.size)
        val content = if ((trimmed[0].toInt() and 0x80) != 0) byteArrayOf(0) + trimmed else trimmed
        require(content.size < 0x80) { "unexpectedly long DER INTEGER" }
        return byteArrayOf(0x02, content.size.toByte()) + content
    }

    private fun derToRaw(der: ByteArray): ByteArray {
        require(der.size >= 8 && der[0] == 0x30.toByte()) { "not a DER SEQUENCE" }
        val seqLen = der[1].toInt() and 0xFF
        require(seqLen < 0x80) { "unsupported long-form DER length" }
        var i = 2
        require(der[i] == 0x02.toByte()) { "expected INTEGER (r)" }
        i++
        val rLen = der[i].toInt() and 0xFF
        i++
        val r = der.copyOfRange(i, i + rLen)
        i += rLen
        require(der[i] == 0x02.toByte()) { "expected INTEGER (s)" }
        i++
        val sLen = der[i].toInt() and 0xFF
        i++
        val s = der.copyOfRange(i, i + sLen)
        return unsignedFixed(BigInteger(1, r), COORD_BYTES) + unsignedFixed(BigInteger(1, s), COORD_BYTES)
    }

    private fun unsignedFixed(v: BigInteger, len: Int): ByteArray {
        val raw = v.toByteArray()
        val trimmed = if (raw.size > len) raw.copyOfRange(raw.size - len, raw.size) else raw
        val out = ByteArray(len)
        trimmed.copyInto(out, len - trimmed.size)
        return out
    }
}
