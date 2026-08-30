package com.thezone.transport

import android.content.Context
import android.util.Log
import com.thezone.demo.HeartbeatSource
import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec

/**
 * Single owner of the active [ReportTransport] for the app process, so the debug
 * screen and the foreground service never fight over the radio. Swappable between
 * BLE / Simulated / File at runtime (the mode picker in the deck's "failure
 * drills").
 *
 * Deliberately coroutine-free and Compose-free: it exposes plain snapshots and a
 * single [onChange] ping. The UI re-reads on the ping.
 */
object TransportController {

    private val lock = Any()
    private var transport: ReportTransport? = null
    private val rows = LinkedHashMap<String, ReceivedRow>()

    @Volatile
    var diagnostics: TransportDiagnostics = TransportDiagnostics(kind = "none")
        private set

    /** Called (on an arbitrary thread) whenever diagnostics or [received] change. */
    @Volatile
    var onChange: (() -> Unit)? = null

    val kind: String get() = transport?.kind ?: "none"

    val received: List<ReceivedRow>
        get() = synchronized(lock) { rows.values.sortedByDescending { it.lastSeenMillis } }

    fun useSimulated() = swap(SimulatedTransport())

    fun useFile() = swap(FileTransport())

    fun useBle(context: Context) = swap(BleTransport(context))

    fun start() {
        transport?.start()
        ping()
    }

    fun stop() {
        transport?.stop()
        ping()
    }

    /** Rebuild this device's heartbeat from live state and (re)advertise it. */
    fun refreshHeartbeat(context: Context) {
        val t = transport ?: return
        runCatching { HeartbeatSource.current(context) }
            .onSuccess { t.advertise(it) }
            .onFailure { diagnosticsError("heartbeat build failed: ${it.message}") }
        ping()
    }

    fun clearReceived() {
        synchronized(lock) { rows.clear() }
        ping()
    }

    /** BLE-only debug switches; no-ops on other transports. */
    fun bleTransport(): BleTransport? = transport as? BleTransport

    private fun swap(next: ReportTransport) {
        synchronized(lock) {
            transport?.stop()
            (transport as? SimulatedTransport)?.shutdown()
            (transport as? BleTransport)?.shutdown()
            rows.clear()
            transport = next
        }
        next.onDiagnostics { d ->
            diagnostics = d
            onChange?.invoke()
        }
        next.onPacket(::ingest)
        diagnostics = next.diagnostics
        ping()
    }

    private fun ingest(inbound: InboundPacket) {
        val decoded = runCatching { PacketCodec.decode(inbound.bytes) }.getOrNull()
        val idHex = runCatching { PacketCodec.contentId(inbound.bytes).toHex() }.getOrNull()
            ?: inbound.bytes.toHex()
        Log.d(
            "TheZone",
            "RX ${inbound.bytes.toHex()} rssi=${inbound.rssi} phy=${inbound.phy} " +
                "dev=${decoded?.deviceId?.toHex() ?: "??"}",
        )
        // Key the debug list by sender so it stays "one row per phone" during the
        // checkpoint. Real content-addressed dedup is H3's job (keyed on contentId).
        val rowKey = decoded?.deviceId?.toHex() ?: idHex
        synchronized(lock) {
            val existing = rows[rowKey]
            rows[rowKey] = ReceivedRow(
                contentIdHex = idHex,
                rawHex = inbound.bytes.toHex(),
                packet = decoded,
                lastRssi = inbound.rssi,
                phy = inbound.phy,
                lastSeenMillis = inbound.receivedAtMillis,
                count = (existing?.count ?: 0) + 1,
            )
            while (rows.size > MAX_ROWS) {
                val oldest = rows.entries.minByOrNull { it.value.lastSeenMillis } ?: break
                rows.remove(oldest.key)
            }
        }
        ping()
    }

    private fun diagnosticsError(message: String) {
        diagnostics = diagnostics.copy(lastError = message, log = diagnostics.log + message)
    }

    private fun ping() = onChange?.invoke()

    private const val MAX_ROWS = 50
}

/** One distinct packet identity heard by this device, for the debug list. */
data class ReceivedRow(
    val contentIdHex: String,
    val rawHex: String,
    val packet: Packet?,
    val lastRssi: Int,
    val phy: PacketPhy,
    val lastSeenMillis: Long,
    val count: Int,
) {
    val deviceIdHex: String get() = packet?.deviceId?.toHex() ?: "??"
    val summary: String
        get() = packet?.let {
            "dev ${deviceIdHex}  st=${it.status}  sev=${it.severity}  bat=${it.batteryLevel}/15  " +
                "hop=${it.hopCount}  next=${it.nextExpectedTxSeconds}s  alt=${if (it.hasBarometer()) it.altDelta else "n/a"}"
        } ?: "undecodable ($rawHex)"
}
