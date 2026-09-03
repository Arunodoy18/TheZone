package com.thezone.transport

import android.content.Context
import android.util.Log
import com.thezone.core.CellConfidence
import com.thezone.core.CellLoss
import com.thezone.core.CorroborationScorer
import com.thezone.core.DeviceSilence
import com.thezone.core.ReportStore
import com.thezone.core.ResolveLog
import com.thezone.core.SilenceEvaluator
import com.thezone.core.SilenceState
import com.thezone.core.SilenceTransition
import com.thezone.core.StoredReport
import com.thezone.core.TriageEntry
import com.thezone.demo.HeartbeatSource
import com.thezone.packet.BatteryScale
import com.thezone.identity.DeviceKeyStore
import com.thezone.packet.PacketCodec
import com.thezone.persistence.StatePersistence
import com.thezone.sensors.LocationReader
import com.thezone.sensors.MotionReader
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
    private val resolveLog = ResolveLog()

    private val pump = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "relay-pump").apply { isDaemon = true }
    }
    private var pumpTask: ScheduledFuture<*>? = null
    private var pumpTick = 0L
    private var lastAdvertisedHex: String? = null
    private var appContext: Context? = null
    private var pressureReader: PressureReader? = null
    private var locationReader: LocationReader? = null
    private var motionReader: MotionReader? = null

    // Tier 0 crash safety: snapshot the store + collapses to disk on a debounce
    // so a reboot / OS kill mid-incident doesn't start a carrier phone blank.
    // BLE only — a Simulated/File demo run must never overwrite real field data.
    @Volatile private var dirty = false
    private var lastSaveMillis = 0L

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

    /** Content-id prefixes a responder has marked reached (RESOLVE, PACKET_SPEC type 1). */
    val resolvedPrefixes: Set<String> get() = resolveLog.all()
    val resolvedCount: Int get() = resolveLog.size
    fun isResolved(contentIdHex: String): Boolean = resolveLog.isResolved(contentIdHex)

    /** Stored reports minus anything a responder has marked reached. */
    private fun activeReports(): List<StoredReport> =
        store.all().filterNot { resolveLog.isResolved(it.contentId) }

    /** Confidence-scored severity per cell (PS5) — trust the picture, don't just colour it. */
    val cellConfidence: List<CellConfidence>
        get() {
            val active = activeReports()
            return CorroborationScorer.scoreCells(active, verifiedResponderDevices(active))
        }

    /** device_id hex of stored RESPONDER packets whose auth verifies against the shared key. */
    private fun verifiedResponderDevices(reports: List<StoredReport>): Set<String> {
        val key = appContext?.let { com.thezone.config.IncidentConfig.responderKey(it) } ?: return emptySet()
        return reports.asSequence()
            .filter { it.packet.status == com.thezone.packet.Status.RESPONDER.code }
            .filter { PacketCodec.verifyAuthWithKey(it.bytes, key) }
            .map { it.packet.deviceId.toHex() }
            .toSet()
    }

    /** True when a confirmed collapse is inside this device's radio horizon. */
    val nearDamage: Boolean get() = com.thezone.demo.NetworkAlert.nearDamage

    /**
     * A self-contained JSON snapshot of the EOC view — reports, cell losses,
     * confidence — for the offline PWA viewer (pwa/eoc.html). Pre-computed;
     * the viewer is purely presentational.
     */
    fun exportEoc(): String {
        fun cell(c: com.thezone.core.GridCell) = """{"lat":${c.latIndex},"lon":${c.lonIndex}}"""
        val now = System.currentTimeMillis()
        val silenceByDev = silence.snapshot().associateBy { it.deviceIdHex }

        val reports = store.all()
            .filterNot { it.isOwn }
            .filterNot { it.packet.type == com.thezone.packet.PacketCodec.TYPE_RESOLVE || resolveLog.isResolved(it.contentId) }
            .joinToString(",") { r ->
            val dev = r.packet.deviceId.toHex()
            val gc = com.thezone.core.GridCells.of(r.packet.deltaLat, r.packet.deltaLon)
                ?: com.thezone.core.GridCells.fallback(dev)
            val st = silenceByDev[dev]?.state?.name ?: "ALIVE"
            """{"deviceId":"$dev","cell":${cell(gc)},"severity":${r.packet.severity},""" +
                """"status":${r.packet.status},"battery":${BatteryScale.nibbleToPercent(r.packet.batteryLevel)},""" +
                """"hops":${r.hopsFromOrigin},"altDelta":${r.packet.altDelta},"altTrend":${r.packet.altTrend},""" +
                """"silence":"$st","lastHeardMs":${now - r.lastHeardAtMillis}}"""
        }
        val cls = silence.cellLosses().joinToString(",") { l ->
            """{"cell":${cell(l.cell)},"deviceCount":${l.deviceCount},"silentCount":${l.silentCount},""" +
                """"firstSilent":${l.firstSilentAtMillis},"lastSilent":${l.lastSilentAtMillis}}"""
        }
        val conf = cellConfidence.joinToString(",") { c ->
            """{"cell":${cell(c.cell)},"severity":${c.severity},"confidence":${"%.3f".format(c.confidence)},""" +
                """"devices":${c.distinctDevices},"pathDiversity":${c.pathDiversity},"verified":${c.hasVerifiedReporter}}"""
        }
        val ageMs = newestReportAgeMillis.let { if (it == Long.MAX_VALUE) -1 else it }
        return """{"v":2,"generatedAt":$now,"generatedBy":"${store.ownDeviceIdHex ?: ""}",""" +
            """"reporters":${reporterCount},"newestReportAgeMs":$ageMs,""" +
            """"reports":[$reports],"cellLosses":[$cls],"confidence":[$conf]}"""
    }

    // --- mesh state as a file (sneakernet, CLAUDE.md "emergency fallback") ---

    /**
     * Every stored raw record + the resolve log, in the [FileTransport] wire
     * format (superset). Two phones with no BLE contact can exchange this file
     * and [importMeshStateJson] merges it — set-union, so it's safe to do both ways.
     */
    fun exportMeshStateJson(): String {
        val packets = store.snapshotRaw().joinToString(",") { "\"${it.toHex()}\"" }
        val resolved = resolveLog.all().joinToString(",") { "\"$it\"" }
        return """{"v":1,"packets":[$packets],"resolved":[$resolved]}"""
    }

    /** Merge a mesh-state file into the store. Returns how many records were new. */
    fun importMeshStateJson(text: String): Int {
        val added = store.mergeFrom(FileTransport.parse(text))
        Regex("\"resolved\"\\s*:\\s*\\[([^]]*)]").find(text)?.groupValues?.get(1)?.let { body ->
            Regex("\"([0-9a-fA-F]{2,64})\"").findAll(body).forEach { resolveLog.add(it.groupValues[1]) }
        }
        if (added > 0) dirty = true
        ping()
        return added
    }

    fun writeMeshStateFile(context: Context): java.io.File {
        val f = java.io.File(context.applicationContext.getExternalFilesDir(null), "thezone-mesh.json")
        f.writeText(exportMeshStateJson())
        return f
    }

    // --- live EOC ------------------------------------------------------------
    // No servers / no network (CLAUDE.md rule 4): instead of a one-shot manual
    // export, the phone holding the picture rewrites the snapshot to its external
    // files dir every few seconds, so `adb pull` / a watched folder / an
    // auto-refreshing viewer always has the current picture.

    /** Distinct non-own devices heard in the last minute — "who is feeding this picture". */
    val reporterCount: Int
        get() {
            val cut = System.currentTimeMillis() - 60_000
            return store.all().asSequence()
                .filterNot { it.isOwn }
                .filter { it.lastHeardAtMillis >= cut }
                .map { it.packet.deviceId.toHex() }
                .distinct().count()
        }

    /** Age of the most recent inbound report, ms (Long.MAX_VALUE if nothing heard). */
    val newestReportAgeMillis: Long
        get() = store.all().filterNot { it.isOwn }
            .maxOfOrNull { it.lastHeardAtMillis }
            ?.let { System.currentTimeMillis() - it }
            ?: Long.MAX_VALUE

    @Volatile var eocAutoExport: Boolean = true
    private var lastEocWriteMillis = 0L

    /** Where the live snapshot is written (adb-pullable without run-as). */
    fun eocLiveFile(context: Context) =
        java.io.File(context.applicationContext.getExternalFilesDir(null), "thezone-eoc.json")

    private fun maybeWriteEoc(context: Context, now: Long) {
        if (!eocAutoExport) return
        if (now - lastEocWriteMillis < EOC_WRITE_PERIOD_MS) return
        lastEocWriteMillis = now
        runCatching {
            val f = eocLiveFile(context)
            val tmp = java.io.File(f.parentFile, "${f.name}.tmp")
            tmp.writeText(exportEoc())
            if (!tmp.renameTo(f)) { tmp.copyTo(f, overwrite = true); tmp.delete() }
        }.onFailure { Log.w("TheZone", "eoc live write failed: ${it.message}") }
    }

    /** Dead Man's Packet — per-device silence state, the transition log, cell losses. */
    val silenceDevices: List<DeviceSilence> get() = silence.snapshot()
    val silenceTransitions: List<SilenceTransition> get() = silence.transitions()
    val cellLosses: List<CellLoss> get() = silence.cellLosses()

    /** How many other devices are currently carrying this phone's signal (heard, not silent). */
    val peersHeard: Int
        get() = silence.snapshot().count {
            it.state == SilenceState.ALIVE || it.state == SilenceState.OVERDUE
        }

    /**
     * Store + silence, joined into the responder's triage rows — one per device.
     * A device whose latest report has been marked reached (RESOLVE) drops off
     * the list, so the picture converges on who still needs help.
     */
    fun triageEntries(): List<TriageEntry> {
        val silenceByDev = silence.snapshot().associateBy { it.deviceIdHex }
        return store.all()
            .filterNot { it.isOwn }
            .filterNot { it.packet.type == com.thezone.packet.PacketCodec.TYPE_RESOLVE }
            .groupBy { it.packet.deviceId.toHex() }
            .map { (_, records) -> records.maxBy { it.lastHeardAtMillis } }
            .filterNot { resolveLog.isResolved(it.contentId) }
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

    fun useSimulated(nodeCount: Int = 500) = swap(SimulatedTransport(nodeCount = nodeCount))

    /** Sim scenario progress 0..1 and the replayed toll, or null when not simulating. */
    val simScenario: Pair<Float, Int>?
        get() = (transport as? SimulatedTransport)?.let { it.progress to it.toll }

    fun useFile() = swap(FileTransport())

    fun useBle(context: Context) = swap(BleTransport(context))

    fun start(context: Context) {
        appContext = context.applicationContext
        store.ownDeviceIdHex = runCatching {
            DeviceKeyStore.identity(context).deviceId.toHex()
        }.getOrNull()
        loadPersisted(appContext!!)
        if (pressureReader == null) pressureReader = PressureReader(appContext!!)
        pressureReader?.start()
        if (locationReader == null) locationReader = LocationReader(appContext!!)
        locationReader?.start()
        if (motionReader == null) motionReader = MotionReader(appContext!!)
        motionReader?.start()
        transport?.start()
        startPump()
        ping()
    }

    fun stop() {
        pumpTask?.cancel(false)
        pumpTask = null
        lastAdvertisedHex = null
        appContext?.let { saveNow(it) }
        pressureReader?.stop()
        locationReader?.stop()
        motionReader?.stop()
        transport?.stop()
        ping()
    }

    /** Reload a persisted snapshot into the store + silence tracker (BLE only). */
    private fun loadPersisted(context: Context) {
        if (transport !is BleTransport) return
        val loaded = StatePersistence.load(context, MAX_RESTORE_AGE_MS) ?: return
        synchronized(lock) {
            store.restore(loaded.reports)
            silence.restoreCellLosses(loaded.cellLosses)
            resolveLog.addAll(loaded.resolvedPrefixes)
            val ownHex = store.ownDeviceIdHex
            loaded.reports.asSequence()
                .filterNot { it.isOwn }
                .map { it.packet.deviceId.toHex() to it }
                .filter { it.first != ownHex }
                .forEach { (dev, r) -> silence.seed(dev, r.packet, r.lastHeardAtMillis) }
        }
        dirty = false
        lastSaveMillis = System.currentTimeMillis()
        ping()
    }

    /** Write the snapshot now, if the active transport is BLE. */
    private fun saveNow(context: Context) {
        if (transport !is BleTransport) return
        dirty = false
        lastSaveMillis = System.currentTimeMillis()
        StatePersistence.save(context, store.all(), silence.cellLosses(), resolveLog.all())
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

    /**
     * A provisioned responder marks a heard device's latest report reached: build
     * a RESOLVE, carry it, put it on air. Returns false if this phone has no
     * responder key or the device isn't in the store.
     */
    fun markResolved(context: Context, deviceIdHex: String): Boolean {
        val ctx = context.applicationContext
        appContext = ctx
        val key = com.thezone.config.IncidentConfig.responderKey(ctx) ?: return false
        val t = transport ?: return false
        val target = store.all()
            .filter {
                !it.isOwn && it.packet.deviceId.toHex() == deviceIdHex &&
                    it.packet.type != PacketCodec.TYPE_RESOLVE
            }
            .maxByOrNull { it.lastHeardAtMillis } ?: return false

        val contentId = PacketCodec.contentId(target.bytes)
        val id = DeviceKeyStore.identity(ctx)
        val pct = HeartbeatSource.effectiveBatteryPercent(ctx)
        val fix = com.thezone.sensors.Position.snapshot()
        val fresh = fix != null && fix.ageMillis() <= 10L * 60 * 1000
        val dLat = if (fresh) com.thezone.packet.GeoPosition.encodeDelta(fix!!.lat, com.thezone.config.IncidentConfig.originLat(ctx)) else com.thezone.packet.Packet.NO_FIX
        val dLon = if (fresh) com.thezone.packet.GeoPosition.encodeDelta(fix!!.lon, com.thezone.config.IncidentConfig.originLon(ctx)) else com.thezone.packet.Packet.NO_FIX

        val bytes = PacketCodec.buildResolve(
            resolver = id,
            responderKey = key,
            resolvedContentId = contentId,
            deltaLat = dLat,
            deltaLon = dLon,
            batteryLevel = BatteryScale.percentToNibble(pct),
            timestampMinutes = com.thezone.packet.EventClock.stampMinutes(System.currentTimeMillis()),
            nextExpectedTxSeconds = HeartbeatSource.ladderSeconds(pct),
            altDelta = com.thezone.sensors.Altitude.deltaByte,
            altTrend = com.thezone.sensors.Altitude.trendMeters,
        )
        store.accept(bytes, rssiDbm = 0)
        resolveLog.add(contentId.copyOfRange(0, PacketCodec.RESOLVE_PREFIX_BYTES).toHex())
        t.advertise(bytes)
        lastAdvertisedHex = bytes.toHex()
        dirty = true
        Log.d("TheZone", "RESOLVE issued for dev=$deviceIdHex cid=${target.contentId.take(14)}")
        ping()
        return true
    }

    /** Whether this phone can issue RESOLVEs (provisioned with the responder key). */
    fun canResolve(context: Context): Boolean =
        com.thezone.config.IncidentConfig.responderKey(context.applicationContext) != null

    fun clearReceived() {
        store.clear()
        silence.clear()
        resolveLog.clear()
        dirty = false
        appContext?.let { StatePersistence.delete(it) }
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
            resolveLog.clear()
            transport = next
        }
        next.onDiagnostics { d ->
            diagnostics = d
            onChange?.invoke()
        }
        next.onPacket(::ingest)
        diagnostics = next.diagnostics
        // switching (back) to BLE: repopulate from the on-disk snapshot
        if (next is BleTransport) appContext?.let { loadPersisted(it) }
        ping()
    }

    private fun ingest(inbound: InboundPacket) {
        val outcome = store.accept(inbound.bytes, inbound.rssi, inbound.receivedAtMillis)
        if (outcome == com.thezone.core.AcceptOutcome.NEW || outcome == com.thezone.core.AcceptOutcome.UPDATED) {
            dirty = true
        }

        // RESOLVE (type 1): only honour it if it verifies against the shared
        // responder key — a forged "reached" from a non-responder is ignored.
        if (PacketCodec.isResolve(inbound.bytes)) {
            val key = appContext?.let { com.thezone.config.IncidentConfig.responderKey(it) }
            if (key != null && PacketCodec.verifyAuthWithKey(inbound.bytes, key)) {
                PacketCodec.resolveTargetPrefix(inbound.bytes)?.let {
                    if (resolveLog.add(it.toHex())) dirty = true
                }
            }
        }

        val packet = runCatching { PacketCodec.decode(inbound.bytes) }.getOrNull()
        if (packet != null) {
            val dev = packet.deviceId.toHex()
            if (dev != store.ownDeviceIdHex) {
                silence.onPacket(dev, packet, inbound.receivedAtMillis)
            }
        }
        // Per-packet logging would flood logcat at H7 scale (500 nodes). The demo
        // watches SILENCE / CELL_LOSS lines from the pump instead.
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

            // Proximity to damage: a collapse whose devices I heard directly
            // (hop <= 1) is inside my radio horizon. Every remaining node near
            // the damage leans in.
            val losses = silence.cellLosses()
            com.thezone.demo.NetworkAlert.nearDamage = losses.isNotEmpty() &&
                store.all().any { r ->
                    !r.isOwn && r.receivedHopCount <= 1 &&
                        (com.thezone.core.GridCells.of(r.packet.deltaLat, r.packet.deltaLon)
                            ?: com.thezone.core.GridCells.fallback(r.packet.deviceId.toHex()))
                            .let { cell -> losses.any { it.cell == cell } }
                }

            // Battery drives the PHY ladder (CLAUDE.md duty cycle): Coded > 60%,
            // alternate 30–60%, 1M-only below — Coded's S=8 airtime fights
            // survival mode, so we drop it as the battery falls. A confirmed
            // collapse nearby overrides the ladder and pins Coded for reach.
            bleTransport()?.let { ble ->
                val pct = HeartbeatSource.effectiveBatteryPercent(ctx)
                ble.phyPolicy = when {
                    com.thezone.demo.NetworkAlert.nearDamage -> BleTransport.PhyPolicy.CODED_ONLY
                    pct > 60 -> BleTransport.PhyPolicy.CODED_ONLY
                    pct >= 30 -> BleTransport.PhyPolicy.ALTERNATE
                    else -> BleTransport.PhyPolicy.ONE_M_ONLY
                }
            }

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
            if (result.transitions.isNotEmpty() || result.newCellLosses.isNotEmpty()) dirty = true

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

            val nowMs = System.currentTimeMillis()
            if (dirty && nowMs - lastSaveMillis >= SAVE_DEBOUNCE_MS) saveNow(ctx)
            maybeWriteEoc(ctx, nowMs)
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

    /** Min gap between on-disk snapshots (crash safety, BLE only). */
    private const val SAVE_DEBOUNCE_MS = 15_000L

    /** How often the live EOC snapshot is rewritten for pull / auto-refresh. */
    private const val EOC_WRITE_PERIOD_MS = 4_000L

    /** On start-up, drop persisted reports/collapses older than this. */
    private const val MAX_RESTORE_AGE_MS = 24L * 60 * 60 * 1000
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
