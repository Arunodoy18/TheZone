package com.thezone.packet

/**
 * The 31-byte STATUS packet, decoded into its semantic fields.
 *
 * This is the wire contract from `docs/PACKET_SPEC.md` and must not drift. `auth`,
 * the device key, and the 7 reserved bytes are deliberately not modelled here:
 * auth is derived at encode time from the sender's key, reserved is always zero,
 * and neither carries round-trippable state.
 *
 * Pure Kotlin — no Android imports. Unit-testable on the JVM.
 */
data class Packet(
    val version: Int,                 // 0..15  protocol version (current = PROTOCOL_VERSION)
    val type: Int,                    // 0..15  packet type (0 = STATUS)
    val deviceId: ByteArray,          // exactly 6 bytes (first 6 of SHA-256(device key))
    val deltaLat: Int,                // int16, or NO_FIX
    val deltaLon: Int,                // int16, or NO_FIX
    val status: Int,                  // 0..15  (see Status)
    val severity: Int,                // 0..15
    val casualties: Int,              // 0..15  (15 = "15 or more")
    val timestampMinutes: Int,        // uint16 minutes since event epoch
    val batteryLevel: Int,            // 0..15  (16 steps over 0..100%)
    val hopCount: Int,                // 0..15
    val nextExpectedTxSeconds: Int,   // uint16 seconds — THE USP field
    val altDelta: Int,                // int8 -127..127, or NO_BAROMETER
    val altTrend: Int,                // int8 -128..127 (metres across last 3 tx)
) {
    fun hasFix(): Boolean = deltaLat != NO_FIX && deltaLon != NO_FIX

    fun hasBarometer(): Boolean = altDelta != NO_BAROMETER

    /** A copy at [newHop], for rebroadcast. Never mutates this instance. */
    fun withHop(newHop: Int): Packet {
        require(newHop in 0..MAX_HOPS) { "hop out of range: $newHop" }
        return copy(hopCount = newHop)
    }

    // deviceId is a ByteArray, so the generated equals/hashCode would compare by
    // reference. Override both to compare by content.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Packet) return false
        return version == other.version &&
            type == other.type &&
            deviceId.contentEquals(other.deviceId) &&
            deltaLat == other.deltaLat &&
            deltaLon == other.deltaLon &&
            status == other.status &&
            severity == other.severity &&
            casualties == other.casualties &&
            timestampMinutes == other.timestampMinutes &&
            batteryLevel == other.batteryLevel &&
            hopCount == other.hopCount &&
            nextExpectedTxSeconds == other.nextExpectedTxSeconds &&
            altDelta == other.altDelta &&
            altTrend == other.altTrend
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + type
        result = 31 * result + deviceId.contentHashCode()
        result = 31 * result + deltaLat
        result = 31 * result + deltaLon
        result = 31 * result + status
        result = 31 * result + severity
        result = 31 * result + casualties
        result = 31 * result + timestampMinutes
        result = 31 * result + batteryLevel
        result = 31 * result + hopCount
        result = 31 * result + nextExpectedTxSeconds
        result = 31 * result + altDelta
        result = 31 * result + altTrend
        return result
    }

    companion object {
        const val SIZE_BYTES = 31
        const val DEVICE_ID_BYTES = 6

        const val PROTOCOL_VERSION = 1
        const val TYPE_STATUS = 0

        const val MAX_HOPS = 15
        const val MAX_NIBBLE = 15

        /** Position sentinel: no GPS fix. Written as 0x8000 in each int16 lane. */
        const val NO_FIX = -32768

        /** alt_delta sentinel: device has no barometer. Written as 0x80. */
        const val NO_BAROMETER = -128
    }
}

/** Status enum (docs/PACKET_SPEC.md "Status enum"). Values 7..15 are reserved. */
enum class Status(val code: Int) {
    UNKNOWN(0),
    SAFE(1),
    TRAPPED_DEBRIS(2),
    RISING_WATER(3),
    INJURED(4),
    HAVE_RESOURCE(5),
    RESPONDER(6),
    ;

    companion object {
        fun fromCode(code: Int): Status? = entries.firstOrNull { it.code == code }
    }
}

/** Battery percent <-> 4-bit nibble (16 steps). See PACKET_SPEC battery_hops. */
object BatteryScale {
    /** 0..100 % -> 0..15. 100 -> 15, 0 -> 0, 50 -> 8. */
    fun percentToNibble(percent: Int): Int {
        val clamped = percent.coerceIn(0, 100)
        return Math.round(clamped * 15.0 / 100.0).toInt()
    }

    /** 0..15 -> representative % (step midpoint-ish), for display only. */
    fun nibbleToPercent(nibble: Int): Int {
        val clamped = nibble.coerceIn(0, Packet.MAX_NIBBLE)
        return Math.round(clamped * 100.0 / 15.0).toInt()
    }
}
