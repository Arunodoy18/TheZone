package com.thezone.transport

/**
 * The transport boundary (CLAUDE.md "Transport is an interface").
 *
 * Everything above this line — Dead Man's Packet, triage, barometry, the store —
 * runs identically whether packets arrive over BLE, from an in-process simulator,
 * or from a JSON file. **No `android.bluetooth` type may cross this boundary.**
 * Inbound data is always a raw 31-byte array plus the small metadata in
 * [InboundPacket]; outbound is a raw 31-byte array.
 */
interface ReportTransport {

    /** Short label for logs / the mode picker: "BLE", "Simulated", "File". */
    val kind: String

    /** Begin scanning for peers, and (if [advertise] was called) broadcasting. */
    fun start()

    /** Stop scanning and broadcasting. Safe to call when already stopped. */
    fun stop()

    /**
     * Set the 31-byte packet this device broadcasts, replacing any current one.
     * Passing `null` clears the advertisement but keeps scanning.
     * @throws IllegalArgumentException if [bytes] is non-null and not 31 bytes.
     */
    fun advertise(bytes: ByteArray?)

    /** Register the sink for every inbound packet. One callback; last call wins. */
    fun onPacket(callback: (InboundPacket) -> Unit)

    /** Register a sink for diagnostic snapshots (state changes, errors, log lines). */
    fun onDiagnostics(callback: (TransportDiagnostics) -> Unit)

    /** Current diagnostic snapshot, also delivered via [onDiagnostics]. */
    val diagnostics: TransportDiagnostics
}

/** A received packet plus reception metadata. Carries no BLE types. */
class InboundPacket(
    /** Exactly 31 bytes — the manufacturer-specific payload, company ID stripped. */
    val bytes: ByteArray,
    /** Signal strength in dBm, for the responder's Dig Here bar (H6). */
    val rssi: Int,
    /** Wall-clock receive time, ms since epoch. */
    val receivedAtMillis: Long,
    /** Which PHY it arrived on — diagnostics only, never affects logic. */
    val phy: PacketPhy,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InboundPacket) return false
        return rssi == other.rssi &&
            receivedAtMillis == other.receivedAtMillis &&
            phy == other.phy &&
            bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + rssi
        result = 31 * result + receivedAtMillis.hashCode()
        result = 31 * result + phy.hashCode()
        return result
    }
}

/**
 * Which PHY an inbound advertisement arrived on. Diagnostics only — never feeds
 * logic. `ONE_M` covers both legacy and extended advertising on the 1M primary
 * channel; `CODED` is LE Long Range (S=8).
 */
enum class PacketPhy { ONE_M, CODED, UNKNOWN }

/**
 * A snapshot of transport state for the in-app log the demo displays. Immutable;
 * a new instance is emitted on every change.
 */
data class TransportDiagnostics(
    val kind: String,
    val running: Boolean = false,
    val advertising: Boolean = false,
    val scanning: Boolean = false,
    /** Hex of the packet currently being broadcast, or null. */
    val advertisedHex: String? = null,
    /**
     * True when extended advertising was unavailable and we fell back to legacy
     * 1M. The 31-byte packet does not fit a legacy PDU with manufacturer framing,
     * so this is a known, logged degradation (BUILD_PLAN H2 "cut").
     */
    val legacyFallbackActive: Boolean = false,
    val codedPhyActive: Boolean = false,
    val oneMPhyActive: Boolean = false,
    val packetsSent: Long = 0,
    val packetsReceived: Long = 0,
    val lastError: String? = null,
    /** Rolling, newest-last, wall-clock-stamped. Capped by the implementation. */
    val log: List<String> = emptyList(),
)
