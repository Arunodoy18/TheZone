package com.thezone.transport

import android.content.Context
import android.util.Log
import com.thezone.core.AcceptOutcome
import com.thezone.core.CellLoss
import com.thezone.core.DeviceSilence
import com.thezone.core.ReportStore
import com.thezone.core.SilenceEvaluator
import com.thezone.core.SilenceState
import com.thezone.core.SilenceTransition
import com.thezone.core.StoredReport
import com.thezone.core.TriageEntry
import com.thezone.demo.HeartbeatSource
import com.thezone.packet.BatteryScale
import com.thezone.identity.DeviceKeyStore
import com.thezone.packet.PacketCodec
import com.thezone.sensors.PressureReader
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * Single owner of the active [ReportTransport] plus the [ReportStore] it feeds, so
 * the debug screen and the foreground service never fight over the radio.
 * Swappable between BLE / Simulated / File at runtime.
 *
 * Runs the relay pump (H3): this device's own heartbeat is advertised
 * continuously; every few ticks a carried packet is swapped in for one window,
 * round-robin, so relaying never starves the own signal (PACKET_SPEC rule 5).
 *
 * Coroutine-free and Compose-free: plain snapshots + a single [onChange] ping.
 */
object TransportController {

    private val lock = Any()
    private var transport: ReportTransport? = null
    private val store = ReportStore()
    private val silence = SilenceEvaluator()

    private val pump = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "relay-pump").apply { isDaemon = true }
    }
    private var pumpTask: ScheduledFuture<*>? = null
    private var pumpTick = 0L
    private var lastAdvertisedHex: String? = null
    private var appContext: Context? = null
    private var pressureReader: PressureReader? = null

    @Volatile
    var diagnostics: TransportDiagnostics = TransportDiagnostics(kind = "none")
        private set

    /** Fired (arbitrary thread) whenever diagnostics, [received] or [reports] change. */
    @Volatile
    var onChange: (() -> Unit)? = null

    val kind: String get() = transport?.kind ?: "none"

    /** The content-addressed store, newest activity first. */
    val reports: List<StoredReport>
        get() = store.all().sortedByDescending { it.lastHeardAtMillis }

    /** Dead Man's Packet — per-device silence state, the transition log, cell losses. */
    val silenceDevices: List<DeviceSilence> get() = silence.snapshot()
    val silenceTransitions: List<SilenceTransition> get() = silence.transitions()
    val cellLosses: List<CellLoss> get() = silence.cellLosses()

    /** How many other devices are currently carrying this phone's signal (heard, not silent). */
    val peersHeard: Int
        get() = silence.snapshot().count {
            it.state == SilenceState.ALIVE || it.state == SilenceState.OVERDUE
        }

    /** Store + silence, joined into the responder's triage rows — one per device. */
    fun triageEntries(): List<TriageEntry> {
        val silenceByDev = silence.snapshot().associateBy { it.deviceIdHex }
        return store.all()
            .filterNot { it.isOwn }
            .groupBy { it.packet.deviceId.toHex() }
            .map { (_, records) -> records.maxBy { it.lastHeardAtMillis } }
            .map { r ->
            val dev = r.packet.deviceId.toHex()
            val s = silenceByDev[dev]
            TriageEntry(
                deviceIdHex = dev,
                status = r.packet.status,
                severity = r.packet.severity,
                casualties = r.packet.casualties,
                altDelta = r.packet.altDelta,
                altTrend = r.packet.altTrend,
                batteryPercent = BatteryScale.nibbleToPercent(r.packet.batteryLevel),
                hopsFromOrigin = r.hopsFromOrigin,
                lastHeardAtMillis = r.lastHeardAtMillis,
                lastRssiDbm = r.lastRssiDbm,
                silence = s?.state ?: SilenceState.ALIVE,
                unexpectedSinceMillis = s?.unexpectedSinceMillis,
            )
        }
    }

    /** Legacy view kept for the debug list — one row per stored report. */
    val received: List<ReceivedRow>
        get() = reports.map { r ->
            ReceivedRow(
                contentIdHex = r.contentId,
                rawHex = r.bytes.toHex(),
                packet = r.packet,
                lastRssi = r.bestRssiDbm,
                phy = PacketPhy.UNKNOWN,
                lastSeenMillis = r.lastHeardAtMillis,
                count = r.timesHeard,
                hopsFromOrigin = r.hopsFromOrigin,
                isOwn = r.isOwn,
            )
        }

    fun useSimulated() = swap(SimulatedTransport())

    fun useFile() = swap(FileTransport())

    fun useBle(context: Context) = swap(BleTransport(context))

    fun start(context: Context) {
        appContext = context.applicationContext
        store.ownDeviceIdHex = runCatching {
            DeviceKeyStore.identity(context).deviceId.toHex()
        }.getOrNull()
        if (pressureReader == null) pressureReader = PressureReader(appContext!!)
        pressureReader?.start()
        transport?.start()
        startPump()
        ping()
    }

    fun stop() {
        pumpTask?.cancel(false)
        pumpTask = null
        lastAdvertisedHex = null
        pressureReader?.stop()
        transport?.stop()
        ping()
    }

    /** Force this device's own heartbeat on air right now (manual debug button). */
    fun refreshHeartbeat(context: Context) {
        appContext = context.applicationContext
        val t = transport ?: return
        runCatching { HeartbeatSource.current(context) }
            .onSuccess {
                store.accept(it, rssiDbm = 0)
                lastAdvertisedHex = it.toHex()
                t.advertise(it)
            }
            .onFailure { diagnosticsError("heartbeat build failed: ${it.message}") }
        ping()
    }

    fun clearReceived() {
        store.clear()
        silence.clear()
        ping()
    }

    fun bleTransport(): BleTransport? = transport as? BleTransport

    private fun swap(next: ReportTransport) {
        synchronized(lock) {
            pumpTask?.cancel(false)
            pumpTask = null
            lastAdvertisedHex = null
            transport?.stop()
            (transport as? SimulatedTransport)?.shutdown()
            (transport as? BleTransport)?.shutdown()
            store.clear()
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
        val outcome = store.accept(inbound.bytes, inbound.rssi, inbound.receivedAtMillis)
        val packet = runCatching { PacketCodec.decode(inbound.bytes) }.getOrNull()
        if (packet != null) {
            val dev = packet.deviceId.toHex()
            if (dev != store.ownDeviceIdHex) {
                silence.onPacket(dev, packet, inbound.receivedAtMillis)
            }
            if (outcome == AcceptOutcome.NEW || outcome == AcceptOutcome.UPDATED) {
                Log.d(
                    "TheZone",
                    "$outcome ${inbound.bytes.toHex()} rssi=${inbound.rssi} phy=${inbound.phy} dev=$dev",
                )
            }
        }
        ping()
    }

    private fun startPump() {
        pumpTask?.cancel(false)
        pumpTick = 0
        pumpTask = pump.scheduleAtFixedRate(
            ::pumpOnce, PUMP_PERIOD_MS, PUMP_PERIOD_MS, TimeUnit.MILLISECONDS,
        )
    }

    private fun pumpOnce() {
        val t = transport ?: return
        val ctx = appContext ?: return
        try {
            pumpTick++

            // Dead Man's Packet: reclassify, and mirror every transition + cell
            // loss to logcat (adb logcat -s TheZone) for the demo.
            val result = silence.tick()
            result.transitions.forEach {
                Log.d(
                    "TheZone",
                    "SILENCE ${it.deviceIdHex} ${it.from}->${it.to} @${it.atMillis} " +
                        "since=${it.sinceLastHeardMillis}ms promised=${it.promisedNextTxSeconds}s bat=${it.batteryPercent}%",
                )
            }
            result.newCellLosses.forEach {
                Log.w(
                    "TheZone",
                    "CELL_LOSS cell=${it.cell} ${it.silentCount}/${it.deviceCount} silent, " +
                        "first@${it.firstSilentAtMillis} last@${it.lastSilentAtMillis}",
                )
            }

            // Don't relay heartbeats for devices we locally believe are silent —
            // that would keep a dead device looking alive downstream.
            val carried =
                if (pumpTick % RELAY_EVERY_N_TICKS == 0L) {
                    store.relayBatch(1) { dev ->
                        when (silence.deviceState(dev)) {
                            SilenceState.EXPECTED_SILENCE, SilenceState.UNEXPECTED_SILENCE -> false
                            else -> true
                        }
                    }
                } else {
                    emptyList()
                }
            val bytes = carried.firstOrNull() ?: runCatching { HeartbeatSource.current(ctx) }
                .onSuccess { store.accept(it, rssiDbm = 0) }
                .getOrNull()
            if (bytes != null) advertiseIfChanged(t, bytes)
        } catch (e: Throwable) {
            diagnosticsError("relay pump: ${e.message}")
        }
        ping()
    }

    private fun advertiseIfChanged(t: ReportTransport, bytes: ByteArray) {
        val hex = bytes.toHex()
        if (hex == lastAdvertisedHex) return
        lastAdvertisedHex = hex
        t.advertise(bytes)
    }

    private fun diagnosticsError(message: String) {
        diagnostics = diagnostics.copy(lastError = message, log = diagnostics.log + message)
    }

    private fun ping() = onChange?.invoke()

    private const val PUMP_PERIOD_MS = 2_000L

    /** 1 tick in N airs a carried packet instead of the own heartbeat. */
    private const val RELAY_EVERY_N_TICKS = 3L
}

/** One stored report, flattened for the debug list. */
data class ReceivedRow(
    val contentIdHex: String,
    val rawHex: String,
    val packet: com.thezone.packet.Packet?,
    val lastRssi: Int,
    val phy: PacketPhy,
    val lastSeenMillis: Long,
    val count: Int,
    val hopsFromOrigin: Int = 0,
    val isOwn: Boolean = false,
) {
    val deviceIdHex: String get() = packet?.deviceId?.toHex() ?: "??"
    val summary: String
        get() = packet?.let {
            val who = if (isOwn) "you" else "dev $deviceIdHex"
            "$who  hops=$hopsFromOrigin  st=${it.status}  sev=${it.severity}  " +
                "bat=${it.batteryLevel}/15  next=${it.nextExpectedTxSeconds}s  " +
                "alt=${if (it.hasBarometer()) it.altDelta else "n/a"}"
        } ?: "undecodable ($rawHex)"
}
