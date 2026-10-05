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

    /** Packet type nibble (low nibble of byte 0). 0 = STATUS, 1 = RESOLVE. */
    const val TYPE_RESOLVE = 1

    /** RESOLVE carries the first N bytes of the resolved report's content-id in reserved[24,31). */
    const val RESOLVE_PREFIX_BYTES = 7

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

    // --- RESOLVE (packet type 1) ---------------------------------------------

    /**
     * Build a RESOLVE packet: responder [resolver] declares the report identified
     * by [resolvedContentId] handled. type = [TYPE_RESOLVE]; the first
     * [RESOLVE_PREFIX_BYTES] of the target content-id go in reserved[24,31);
     * `auth` is MAC'd with [responderKey] so only provisioned responders can
     * issue one. The remaining fields carry the responder's own live state, so
     * the packet doubles as proof the responder is alive.
     */
    fun buildResolve(
        resolver: DeviceIdentity,
        responderKey: ByteArray,
        resolvedContentId: ByteArray,
        deltaLat: Int,
        deltaLon: Int,
        batteryLevel: Int,
        timestampMinutes: Int,
        nextExpectedTxSeconds: Int,
        altDelta: Int = Packet.NO_BAROMETER,
        altTrend: Int = 0,
    ): ByteArray {
        require(resolvedContentId.size >= RESOLVE_PREFIX_BYTES) { "content-id too short" }
        val p = Packet(
            version = Packet.PROTOCOL_VERSION,
            type = TYPE_RESOLVE,
            deviceId = resolver.deviceId,
            deltaLat = deltaLat,
            deltaLon = deltaLon,
            status = Status.RESPONDER.code,
            severity = 0,
            casualties = 0,
            timestampMinutes = timestampMinutes,
            batteryLevel = batteryLevel,
            hopCount = 0,
            nextExpectedTxSeconds = nextExpectedTxSeconds,
            altDelta = altDelta,
            altTrend = altTrend,
        )
        // auth covers [0,19) only, so writing reserved[24,31) after encode is safe
        val out = encode(p, resolver, authKey = responderKey)
        resolvedContentId.copyInto(out, OFF_RESERVED, 0, RESOLVE_PREFIX_BYTES)
        return out
    }

    fun isResolve(bytes: ByteArray): Boolean =
        bytes.size == Packet.SIZE_BYTES &&
            (bytes[OFF_VERSION_TYPE].toInt() and 0x0F) == TYPE_RESOLVE

    /** The target content-id prefix from a RESOLVE packet, or null if it isn't one. */
    fun resolveTargetPrefix(bytes: ByteArray): ByteArray? =
        if (isResolve(bytes)) bytes.copyOfRange(OFF_RESERVED, OFF_RESERVED + RESOLVE_PREFIX_BYTES) else null

    // --- STATUS phrase code --------------------------------------------
    // A STATUS packet's reserved[24] byte (docs/PACKET_SPEC.md "reserved
    // zero-filled in a STATUS packet") carries an optional fixed-phrase code —
    // the same mechanism ALERT already uses for its phrase code, applied to an
    // ordinary report. 0 = "no phrase chosen" (the existing default, so every
    // STATUS packet ever encoded before this still decodes the same way).
    // See com.thezone.packet.StatusPhrases for the table itself.

    /** Returns a copy of [bytes] with reserved[24] set to [phraseCode] (1..255). Only meaningful on a STATUS packet. */
    fun withStatusPhrase(bytes: ByteArray, phraseCode: Int): ByteArray {
        require(bytes.size == Packet.SIZE_BYTES)
        require(phraseCode in 1..255) { "phraseCode must be 1..255 (0 means 'none')" }
        val out = bytes.copyOf()
        out[OFF_RESERVED] = phraseCode.toByte()
        return out
    }

    /** The chosen phrase code on a STATUS packet, or null if it's a different type or none was chosen. */
    fun statusPhraseCode(bytes: ByteArray): Int? {
        if (bytes.size != Packet.SIZE_BYTES) return null
        if ((bytes[OFF_VERSION_TYPE].toInt() and 0x0F) != 0 /* TYPE_STATUS */) return null
        val code = bytes[OFF_RESERVED].toInt() and 0xFF
        return if (code == 0) null else code
    }

    // --- ALERT (packet type 2) --------------------------------------------
    // A government-style emergency alert that floods the mesh. Same 31 bytes,
    // fields re-purposed: status = category (0..4), next_expected_tx = minutes the
    // alert stays valid, alt_delta = radius in units of 20 m (unsigned 0..255),
    // reserved[24] = phrase code (0..255). auth is MAC'd with the pre-shared
    // responder / authority key so only provisioned phones can issue one.

    const val TYPE_ALERT = 2

    /** Alert severity category. */
    const val ALERT_INFO = 0
    const val ALERT_ADVISORY = 1
    const val ALERT_WATCH = 2
    const val ALERT_WARNING = 3
    const val ALERT_EXTREME = 4

    private const val ALERT_RADIUS_UNIT_M = 20

    fun buildAlert(
        issuer: DeviceIdentity,
        authorityKey: ByteArray,
        category: Int,
        phraseCode: Int,
        deltaLat: Int,
        deltaLon: Int,
        radiusMeters: Int,
        issuedAtMinutes: Int,
        validForMinutes: Int,
        batteryLevel: Int,
    ): ByteArray {
        val out = ByteArray(Packet.SIZE_BYTES)
        out[OFF_VERSION_TYPE] =
            (((Packet.PROTOCOL_VERSION and 0x0F) shl 4) or (TYPE_ALERT and 0x0F)).toByte()
        issuer.deviceId.copyInto(out, OFF_DEVICE_ID, 0, Packet.DEVICE_ID_BYTES)
        putInt16(out, OFF_POSITION, deltaLat)
        putInt16(out, OFF_POSITION + 2, deltaLon)
        out[OFF_STATUS] = (category.coerceIn(0, 15) and 0xFF).toByte()
        out[OFF_SEVERITY_CASUALTIES] = 0
        putUint16(out, OFF_TIMESTAMP, issuedAtMinutes and 0xFFFF)
        out[OFF_BATTERY_HOPS] = ((batteryLevel and 0x0F) shl 4).toByte() // hop 0
        putUint16(out, OFF_NEXT_TX, validForMinutes.coerceIn(0, 65535))
        out[OFF_ALT_DELTA] = ((radiusMeters / ALERT_RADIUS_UNIT_M).coerceIn(0, 255)).toByte()
        val auth = DeviceIdentity.sha256(authorityKey, out.copyOfRange(0, AUTH_COVERAGE_END))
        auth.copyInto(out, OFF_AUTH, 0, AUTH_BYTES)
        out[OFF_ALT_TREND] = 0
        out[OFF_RESERVED] = (phraseCode and 0xFF).toByte()
        return out
    }

    fun isAlert(bytes: ByteArray): Boolean =
        bytes.size == Packet.SIZE_BYTES &&
            (bytes[OFF_VERSION_TYPE].toInt() and 0x0F) == TYPE_ALERT

    data class AlertFields(
        val category: Int,
        val phraseCode: Int,
        val deltaLat: Int,
        val deltaLon: Int,
        val radiusMeters: Int,
        val issuedAtMinutes: Int,
        val validForMinutes: Int,
        val hopCount: Int,
        val issuerHex: String,
    )

    fun decodeAlert(bytes: ByteArray): AlertFields {
        require(isAlert(bytes)) { "not an ALERT packet" }
        return AlertFields(
            category = bytes[OFF_STATUS].toInt() and 0xFF,
            phraseCode = bytes[OFF_RESERVED].toInt() and 0xFF,
            deltaLat = getInt16(bytes, OFF_POSITION),
            deltaLon = getInt16(bytes, OFF_POSITION + 2),
            radiusMeters = (bytes[OFF_ALT_DELTA].toInt() and 0xFF) * ALERT_RADIUS_UNIT_M,
            issuedAtMinutes = getUint16(bytes, OFF_TIMESTAMP),
            validForMinutes = getUint16(bytes, OFF_NEXT_TX),
            hopCount = bytes[OFF_BATTERY_HOPS].toInt() and 0x0F,
            issuerHex = bytes.copyOfRange(OFF_DEVICE_ID, OFF_DEVICE_ID + Packet.DEVICE_ID_BYTES)
                .joinToString("") { "%02x".format(it) },
        )
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

    // --- SIG (packet type 3) -----------------------------------------------
    // A fragment of an ECDSA P-256 signature over another packet's content-id
    // — see com.thezone.identity (EcdsaSignature, TrustRoster,
    // ResponderSigningKey) and com.thezone.core.SignatureLog. Closes the gap
    // the pre-shared responder key leaves open: anyone holding that one key
    // can forge a RESOLVE or ALERT. A SIG fragment only means something once
    // its signer's device_id is in TrustRoster.PILOT.
    //
    // A raw ECDSA signature is 64 fixed-width bytes (r || s) — more than fits
    // in the 31-byte envelope alongside a linking reference, so it rides as 4
    // fragments of 16 bytes each. The 31 bytes split as:
    //
    //   [0]      version_type (type = TYPE_SIG)
    //   [1,7)    device_id — the signer
    //   [7]      meta: high nibble = target packet type (1 RESOLVE / 2 ALERT),
    //            low nibble = fragment index 0..3
    //   [8,10)   target content-id prefix (2 bytes) — which RESOLVE/ALERT this
    //            fragment signs; a coarse link, disambiguated further by
    //            (signer, target type) — see SignatureLog.sweep
    //   [15]     hop byte — same generic meaning as every other packet type
    //            (relay-managed; contentId masks its low nibble). High nibble
    //            unused.
    //   [19,23)  the legacy `auth` field slot — unused here (trust comes from
    //            the signature, not a MAC), filled with a nonzero placeholder
    //            only to satisfy the generic "auth shape" relay check
    //   everywhere else (18 bytes: [10,15), [16,19), [23,31)) — the 16-byte
    //   signature chunk, written across those three windows in order
    const val TYPE_SIG = 3
    const val SIG_FRAGMENT_BYTES = 16
    const val SIG_FRAGMENT_COUNT = 4 // 4 * 16 = 64-byte raw ECDSA signature
    const val SIG_TARGET_PREFIX_BYTES = 2

    private const val OFF_SIG_META = 7
    private const val OFF_SIG_TARGET_PREFIX = 8

    // (offset, length) windows the 16 chunk bytes are written across, in order —
    // every packet byte except version/device_id/meta/target-prefix/hop/auth-slot.
    private val SIG_CHUNK_WINDOWS = listOf(10 to 5, 16 to 3, 23 to 8)

    private fun writeSigChunk(out: ByteArray, chunk: ByteArray) {
        var pos = 0
        for ((off, len) in SIG_CHUNK_WINDOWS) {
            chunk.copyInto(out, off, pos, pos + len)
            pos += len
        }
    }

    private fun readSigChunk(bytes: ByteArray): ByteArray {
        val out = ByteArray(SIG_FRAGMENT_BYTES)
        var pos = 0
        for ((off, len) in SIG_CHUNK_WINDOWS) {
            bytes.copyInto(out, pos, off, off + len)
            pos += len
        }
        return out
    }

    /**
     * Build one fragment of a signature over [targetContentId] (32 bytes, i.e.
     * [contentId] of the RESOLVE/ALERT being signed). [signatureChunk] is 16
     * bytes: `rawSignature.copyOfRange(fragmentIndex * 16, fragmentIndex * 16 + 16)`.
     */
    fun buildSigFragment(
        signerDeviceId: ByteArray,
        targetType: Int,
        targetContentId: ByteArray,
        fragmentIndex: Int,
        signatureChunk: ByteArray,
    ): ByteArray {
        require(signerDeviceId.size == Packet.DEVICE_ID_BYTES) { "signerDeviceId must be ${Packet.DEVICE_ID_BYTES} bytes" }
        require(targetType == TYPE_RESOLVE || targetType == TYPE_ALERT) { "targetType must be RESOLVE or ALERT" }
        require(targetContentId.size >= SIG_TARGET_PREFIX_BYTES) { "targetContentId too short" }
        require(fragmentIndex in 0 until SIG_FRAGMENT_COUNT) { "fragmentIndex out of range" }
        require(signatureChunk.size == SIG_FRAGMENT_BYTES) { "signatureChunk must be $SIG_FRAGMENT_BYTES bytes" }

        val out = ByteArray(Packet.SIZE_BYTES)
        out[OFF_VERSION_TYPE] = (((Packet.PROTOCOL_VERSION and 0x0F) shl 4) or (TYPE_SIG and 0x0F)).toByte()
        signerDeviceId.copyInto(out, OFF_DEVICE_ID, 0, Packet.DEVICE_ID_BYTES)
        val meta = (((targetType and 0x0F) shl 4) or (fragmentIndex and 0x0F)).toByte()
        out[OFF_SIG_META] = meta
        targetContentId.copyInto(out, OFF_SIG_TARGET_PREFIX, 0, SIG_TARGET_PREFIX_BYTES)
        writeSigChunk(out, signatureChunk)
        // auth[19,23) carries no MAC here — fill with the (always-nonzero) meta
        // byte purely so the generic relay-layer auth-shape check passes.
        for (i in 0 until 4) out[OFF_AUTH + i] = meta
        return out
    }

    fun isSig(bytes: ByteArray): Boolean =
        bytes.size == Packet.SIZE_BYTES && (bytes[OFF_VERSION_TYPE].toInt() and 0x0F) == TYPE_SIG

    data class SigFragment(
        val signerDeviceIdHex: String,
        val targetType: Int,
        val fragmentIndex: Int,
        val targetContentIdPrefixHex: String,
        val chunk: ByteArray,
    )

    fun decodeSigFragment(bytes: ByteArray): SigFragment? {
        if (!isSig(bytes)) return null
        val meta = bytes[OFF_SIG_META].toInt() and 0xFF
        val targetType = (meta ushr 4) and 0x0F
        if (targetType != TYPE_RESOLVE && targetType != TYPE_ALERT) return null
        return SigFragment(
            signerDeviceIdHex = bytes.copyOfRange(OFF_DEVICE_ID, OFF_DEVICE_ID + Packet.DEVICE_ID_BYTES)
                .joinToString("") { "%02x".format(it) },
            targetType = targetType,
            fragmentIndex = meta and 0x0F,
            targetContentIdPrefixHex = bytes
                .copyOfRange(OFF_SIG_TARGET_PREFIX, OFF_SIG_TARGET_PREFIX + SIG_TARGET_PREFIX_BYTES)
                .joinToString("") { "%02x".format(it) },
            chunk = readSigChunk(bytes),
        )
    }
}
