package com.thezone.packet

import java.security.MessageDigest

/**
 * encode / decode for the 31-byte STATUS packet. Pure Kotlin, no Android.
 *
 * The byte layout is authoritative in `docs/PACKET_SPEC.md`. All multi-byte
 * values are big-endian. This object also owns the two hash decisions the relay
 * and the CRDT store depend on ([contentId] vs [rawHash]) — see [contentId].
 */
object PacketCodec {

    // Field offsets — docs/PACKET_SPEC.md "Layout".
    private const val OFF_VERSION_TYPE = 0
    private const val OFF_DEVICE_ID = 1
    private const val OFF_POSITION = 7            // int16 lat, then int16 lon
    private const val OFF_STATUS = 11
    private const val OFF_SEVERITY_CASUALTIES = 12
    private const val OFF_TIMESTAMP = 13
    private const val OFF_BATTERY_HOPS = 15
    private const val OFF_NEXT_TX = 16
    private const val OFF_ALT_DELTA = 18
    private const val OFF_AUTH = 19
    private const val OFF_ALT_TREND = 23
    private const val OFF_RESERVED = 24

    private const val AUTH_BYTES = 4

    /** auth signs payload bytes [0, 19) — everything before the auth field itself. */
    private const val AUTH_COVERAGE_END = OFF_AUTH

    private const val ALT_NO_BAROMETER_BYTE = 0x80

    // --- encode -------------------------------------------------------------

    /**
     * Encode [packet], signing with [identity] — or, when [authKey] is given,
     * MAC the `auth` field with that key instead (the pre-shared responder key:
     * a RESPONDER packet any phone holding the key can verify). Always returns
     * exactly [Packet.SIZE_BYTES] bytes. Throws if any field is out of range or
     * if [identity] does not own [Packet.deviceId].
     */
    fun encode(packet: Packet, identity: DeviceIdentity, authKey: ByteArray? = null): ByteArray {
        validate(packet)
        require(identity.deviceId.contentEquals(packet.deviceId)) {
            "packet.deviceId does not match the signing identity"
        }

        val out = ByteArray(Packet.SIZE_BYTES)

        out[OFF_VERSION_TYPE] =
            (((packet.version and 0x0F) shl 4) or (packet.type and 0x0F)).toByte()

        packet.deviceId.copyInto(out, OFF_DEVICE_ID, 0, Packet.DEVICE_ID_BYTES)

        putInt16(out, OFF_POSITION, packet.deltaLat)
        putInt16(out, OFF_POSITION + 2, packet.deltaLon)

        out[OFF_STATUS] = (packet.status and 0xFF).toByte()

        out[OFF_SEVERITY_CASUALTIES] =
            (((packet.severity and 0x0F) shl 4) or (packet.casualties and 0x0F)).toByte()

        putUint16(out, OFF_TIMESTAMP, packet.timestampMinutes)

        out[OFF_BATTERY_HOPS] =
            (((packet.batteryLevel and 0x0F) shl 4) or (packet.hopCount and 0x0F)).toByte()

        putUint16(out, OFF_NEXT_TX, packet.nextExpectedTxSeconds)

        out[OFF_ALT_DELTA] =
            if (packet.altDelta == Packet.NO_BAROMETER) {
                ALT_NO_BAROMETER_BYTE.toByte()
            } else {
                packet.altDelta.coerceIn(-127, 127).toByte()
            }

        // auth = SHA-256(key ‖ out[0, 19))[0, 4) — key is the responder key when signing a RESPONDER packet
        val auth = DeviceIdentity.sha256(authKey ?: identity.key, out.copyOfRange(0, AUTH_COVERAGE_END))
        auth.copyInto(out, OFF_AUTH, 0, AUTH_BYTES)

        out[OFF_ALT_TREND] = packet.altTrend.coerceIn(-128, 127).toByte()

        // reserved [24, 31) stays zero.
        return out
    }

    // --- decode -----------------------------------------------------------

    fun decode(bytes: ByteArray): Packet {
        require(bytes.size == Packet.SIZE_BYTES) {
            "packet must be ${Packet.SIZE_BYTES} bytes, got ${bytes.size}"
        }

        val versionType = bytes[OFF_VERSION_TYPE].toInt() and 0xFF
        val severityCasualties = bytes[OFF_SEVERITY_CASUALTIES].toInt() and 0xFF
        val batteryHops = bytes[OFF_BATTERY_HOPS].toInt() and 0xFF
        val altRaw = bytes[OFF_ALT_DELTA].toInt() // sign-extended

        return Packet(
            version = (versionType ushr 4) and 0x0F,
            type = versionType and 0x0F,
            deviceId = bytes.copyOfRange(OFF_DEVICE_ID, OFF_DEVICE_ID + Packet.DEVICE_ID_BYTES),
            deltaLat = getInt16(bytes, OFF_POSITION),
            deltaLon = getInt16(bytes, OFF_POSITION + 2),
            status = bytes[OFF_STATUS].toInt() and 0xFF,
            severity = (severityCasualties ushr 4) and 0x0F,
            casualties = severityCasualties and 0x0F,
            timestampMinutes = getUint16(bytes, OFF_TIMESTAMP),
            batteryLevel = (batteryHops ushr 4) and 0x0F,
            hopCount = batteryHops and 0x0F,
            nextExpectedTxSeconds = getUint16(bytes, OFF_NEXT_TX),
            altDelta =
                if ((altRaw and 0xFF) == ALT_NO_BAROMETER_BYTE) Packet.NO_BAROMETER else altRaw,
            altTrend = bytes[OFF_ALT_TREND].toInt(),
        )
    }

    // --- relay ----------------------------------------------------------

    /** hop_count from a raw packet, without a full decode. */
    fun hopCount(bytes: ByteArray): Int = bytes[OFF_BATTERY_HOPS].toInt() and 0x0F

    /**
     * A fresh copy with hop_count incremented, capped at [Packet.MAX_HOPS]. The
     * input array is never mutated (relay rule 3: never touch the stored original).
     */
    fun incrementHop(bytes: ByteArray): ByteArray {
        require(bytes.size == Packet.SIZE_BYTES)
        val copy = bytes.copyOf()
        val batteryHops = copy[OFF_BATTERY_HOPS].toInt() and 0xFF
        val nextHop = ((batteryHops and 0x0F) + 1).coerceAtMost(Packet.MAX_HOPS)
        copy[OFF_BATTERY_HOPS] = ((batteryHops and 0xF0) or nextHop).toByte()
        return copy
    }

    // --- identity / dedup ---------------------------------------------

    /**
     * Content identity for dedup and CRDT set-union (docs/PACKET_SPEC.md test 8).
     *
     * Deliberate decision: identity is the **message**, not its journey. We hash
     * bytes [0, 19) and [23, 31), with the hop nibble of byte 15 masked to zero
     * and the 4 auth bytes [19, 23) excluded.
     *
     * Why exclude both: hop_count sits in the low nibble of byte 15, which is
     * inside auth's coverage, so *every relay changes both the hop nibble and the
     * auth bytes*. If either fed the identity hash, the same report would re-enter
     * the store at every hop. Masking the hop nibble and dropping auth makes two
     * copies of one report — heard at different hop counts — dedup correctly.
     */
    fun contentId(bytes: ByteArray): ByteArray {
        require(bytes.size == Packet.SIZE_BYTES)
        val canonical = bytes.copyOf()
        canonical[OFF_BATTERY_HOPS] = (canonical[OFF_BATTERY_HOPS].toInt() and 0xF0).toByte()

        val md = MessageDigest.getInstance("SHA-256")
        md.update(canonical, 0, OFF_AUTH)                                   // [0, 19)
        md.update(canonical, OFF_ALT_TREND, Packet.SIZE_BYTES - OFF_ALT_TREND) // [23, 31)
        return md.digest()
    }

    /**
     * Naive hash over all 31 bytes — journey-sensitive (hop and auth included).
     * Kept only to make the [contentId] decision explicit and testable; the store
     * must key on [contentId], never this.
     */
    fun rawHash(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    // --- auth --------------------------------------------------------

    fun authBytes(bytes: ByteArray): ByteArray =
        bytes.copyOfRange(OFF_AUTH, OFF_AUTH + AUTH_BYTES)

    /** Relay rule 1: auth must be present (4 bytes) and not all-zero. No key needed. */
    fun authShapeValid(bytes: ByteArray): Boolean {
        if (bytes.size != Packet.SIZE_BYTES) return false
        for (i in OFF_AUTH until OFF_AUTH + AUTH_BYTES) {
            if (bytes[i].toInt() != 0) return true
        }
        return false
    }

    /** Full check: recompute auth from [identity]'s key and compare. */
    fun verifyAuth(bytes: ByteArray, identity: DeviceIdentity): Boolean =
        verifyAuthWithKey(bytes, identity.key)

    /**
     * Verify `auth` against a raw key. Used with the pre-shared responder key: a
     * packet claiming `Status.RESPONDER` is only trusted as a responder if this
     * returns true.
     */
    fun verifyAuthWithKey(bytes: ByteArray, key: ByteArray): Boolean {
        require(bytes.size == Packet.SIZE_BYTES)
        val expected = DeviceIdentity.sha256(key, bytes.copyOfRange(0, AUTH_COVERAGE_END))
        for (i in 0 until AUTH_BYTES) {
            if (expected[i] != bytes[OFF_AUTH + i]) return false
        }
        return true
    }

    // --- validation / primitives -----------------------------------

    private fun validate(p: Packet) {
        requireNibble(p.version, "version")
        requireNibble(p.type, "type")
        require(p.deviceId.size == Packet.DEVICE_ID_BYTES) {
            "deviceId must be ${Packet.DEVICE_ID_BYTES} bytes, got ${p.deviceId.size}"
        }
        requireInt16(p.deltaLat, "deltaLat")
        requireInt16(p.deltaLon, "deltaLon")
        require(p.status in 0..255) { "status out of range: ${p.status}" }
        requireNibble(p.severity, "severity")
        requireNibble(p.casualties, "casualties")
        requireUint16(p.timestampMinutes, "timestampMinutes")
        requireNibble(p.batteryLevel, "batteryLevel")
        requireNibble(p.hopCount, "hopCount")
        requireUint16(p.nextExpectedTxSeconds, "nextExpectedTxSeconds")
        require(p.altDelta == Packet.NO_BAROMETER || p.altDelta in -127..127) {
            "altDelta out of range: ${p.altDelta}"
        }
        require(p.altTrend in -128..127) { "altTrend out of range: ${p.altTrend}" }
    }

    private fun requireNibble(v: Int, name: String) =
        require(v in 0..Packet.MAX_NIBBLE) { "$name must be 0..15, got $v" }

    private fun requireInt16(v: Int, name: String) =
        require(v == Packet.NO_FIX || v in -32767..32767) { "$name out of int16 range: $v" }

    private fun requireUint16(v: Int, name: String) =
        require(v in 0..65535) { "$name out of uint16 range: $v" }

    private fun putUint16(out: ByteArray, off: Int, value: Int) {
        out[off] = ((value ushr 8) and 0xFF).toByte()
        out[off + 1] = (value and 0xFF).toByte()
    }

    private fun putInt16(out: ByteArray, off: Int, value: Int) =
        putUint16(out, off, value and 0xFFFF)

    private fun getUint16(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF)

    private fun getInt16(b: ByteArray, off: Int): Int {
        val u = getUint16(b, off)
        return if (u >= 0x8000) u - 0x10000 else u
    }
}
