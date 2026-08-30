package com.thezone.transport

import com.thezone.packet.DeviceIdentity
import com.thezone.packet.EventClock
import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec
import com.thezone.packet.Status
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Demo insurance + the PS5 scale story (H7). An in-process, radio-free transport
 * that seeds [nodeCount] synthetic devices over Rasuwagadhi geography and
 * replays the real toll curve (22 → 95 → 469 → 626 over 72 h) compressed to
 * [scenarioSeconds] of stage time.
 *
 * Two events are *scripted*, not left to chance (BUILD_PLAN): a whole cluster
 * goes dark inside a few seconds → CELL_LOSS on the map; and two lone nodes go
 * dark — one on a healthy battery (UNEXPECTED_SILENCE) and one on a critical
 * battery (EXPECTED_SILENCE) — so the T3/T4 distinction demos from the simulator
 * alone.
 *
 * Pure Kotlin apart from the scheduler. No Android, no radio.
 */
class SimulatedTransport(
    private val nodeCount: Int = 500,
    private val scenarioSeconds: Int = 90,
    private val seed: Long = 0x5EED,
) : BaseTransport(kind = "Simulated") {

    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "sim-transport").apply { isDaemon = true }
    }
    private var task: ScheduledFuture<*>? = null

    private val rng = Random(seed)
    private val nodes: List<SimNode> = buildScenario()
    private var startedAtMillis = 0L

    /** 0f..1f progress through the scenario. */
    val progress: Float
        get() {
            if (startedAtMillis == 0L) return 0f
            return ((System.currentTimeMillis() - startedAtMillis) / (scenarioSeconds * 1000f))
                .coerceIn(0f, 1f)
        }

    /** Current replayed toll (nodes at or past their severe moment). */
    val toll: Int get() = nodes.count { progress >= it.severeAtT }

    override fun start() {
        if (running) return
        running = true
        scanning = true
        startedAtMillis = System.currentTimeMillis()
        nodes.forEach { it.nextEmitAtMillis = startedAtMillis + it.startOffsetMillis }
        log("simulated scenario start — $nodeCount nodes, ${scenarioSeconds}s, scripted CELL_LOSS + T3/T4")
        task = executor.scheduleAtFixedRate(::pump, 0L, TICK_MS, TimeUnit.MILLISECONDS)
        emitDiagnostics()
    }

    override fun stop() {
        if (!running) return
        task?.cancel(false)
        task = null
        running = false
        scanning = false
        advertising = false
        log("simulated scenario stop  (toll ${toll}, progress ${(progress * 100).roundToInt()}%)")
        emitDiagnostics()
    }

    override fun advertise(bytes: ByteArray?) {
        requirePacketSize(bytes)
        advertisedHex = bytes?.toHex()
        advertising = bytes != null
        emitDiagnostics()
    }

    fun shutdown() {
        stop()
        executor.shutdownNow()
    }

    private fun pump() {
        try {
            val now = System.currentTimeMillis()
            val t = progress
            for (node in nodes) {
                if (now < node.nextEmitAtMillis) continue
                node.nextEmitAtMillis = now + node.intervalMillis
                val bytes = node.emit(t, now) ?: continue // null = scripted silent
                deliver(
                    InboundPacket(
                        bytes = bytes,
                        rssi = node.rssi,
                        receivedAtMillis = now,
                        phy = if (node.fastBeacon) PacketPhy.CODED else PacketPhy.ONE_M,
                    ),
                )
            }
        } catch (e: Throwable) {
            fail("sim pump: ${e.message}")
        }
    }

    // --- scenario construction ----------------------------------------

    private fun buildScenario(): List<SimNode> {
        // A handful of cluster centres (villages / structures) around the origin,
        // in position-delta units (~1.1 m each). ~900 units ≈ 1 km.
        val clusters = List(CLUSTER_COUNT) {
            IntArray(2).also { c ->
                c[0] = rng.nextInt(-900, 900)
                c[1] = rng.nextInt(-900, 900)
            }
        }

        // A dedicated "doomed building": every node at the exact centre of one
        // grid cell, well away from the other clusters, so that cell can read
        // 100% silent -> a clean, unambiguous CELL_LOSS.
        val cell = 90
        val doomedCentre = intArrayOf(
            (rng.nextInt(-6, 7) * cell) + cell / 2,
            (rng.nextInt(-6, 7) * cell) + cell / 2,
        )
        val doomedCount = minOf(DOOMED_COUNT, nodeCount / 4)

        val list = ArrayList<SimNode>(nodeCount)
        repeat(nodeCount) { i ->
            val doomed = i >= nodeCount - doomedCount
            val cluster = if (doomed) CLUSTER_COUNT else i % CLUSTER_COUNT
            val centre = if (doomed) doomedCentre else clusters[cluster]
            val spread = if (doomed) 0 else SPREAD
            val key = ByteArray(DeviceIdentity.KEY_BYTES) { rng.nextInt().toByte() }
            val identity = DeviceIdentity(key)

            // toll-curve inverse: a node "becomes severe" at the t where the
            // replayed toll fraction first reaches its draw.
            val severeAtT = tSuchThatTollFractionReaches(rng.nextDouble())

            // most nodes healthy; a slice on low battery
            val batteryLevel = if (rng.nextInt(100) < 12) rng.nextInt(1, 3) else rng.nextInt(7, 16)

            list += SimNode(
                identity = identity,
                deltaLat = (centre[0] + gaussian(spread)).coerceIn(-32000, 32000),
                deltaLon = (centre[1] + gaussian(spread)).coerceIn(-32000, 32000),
                cluster = cluster,
                severeAtT = severeAtT,
                baseSeverity = rng.nextInt(0, 4),
                severeSeverity = rng.nextInt(9, 16),
                batteryLevel = batteryLevel,
                rssi = -50 - rng.nextInt(45),
                fastBeacon = false,
                intervalMillis = rng.nextLong(20_000, 40_000),
                silentAtT = null,
                startOffsetMillis = rng.nextLong(0, 3_000),
            )
        }

        // Guarantee the doomed cell holds only doomed nodes: nudge any stray
        // background node out of it (rare, but it would dilute the 80% rule).
        val doomedCell = intArrayOf(Math.floorDiv(doomedCentre[0], cell), Math.floorDiv(doomedCentre[1], cell))
        list.forEachIndexed { i, n ->
            if (i < nodeCount - doomedCount &&
                Math.floorDiv(n.deltaLat, cell) == doomedCell[0] &&
                Math.floorDiv(n.deltaLon, cell) == doomedCell[1]
            ) {
                n.deltaLat += cell * 2
            }
        }

        scriptEvents(list)
        return list
    }

    private fun scriptEvents(nodes: MutableList<SimNode>) {
        // 1. the whole doomed building goes dark within a few seconds -> CELL_LOSS.
        //    It is its own tight cluster, so the cell reads 100% silent.
        val doomed = nodes.filter { it.cluster == CLUSTER_COUNT }
        doomed.forEach {
            it.fastBeacon = true
            it.intervalMillis = 2_000
            it.batteryLevel = it.batteryLevel.coerceIn(6, 15) // not "expected" — this is a collapse
            it.silentAtT = (0.62 + rng.nextDouble() * 0.05)    // ~5 s window mid-scenario
        }

        // 2. a lone node elsewhere, healthy battery, goes dark -> UNEXPECTED_SILENCE (T3)
        nodes.first { it.cluster != CLUSTER_COUNT && it.batteryLevel >= 10 }.apply {
            fastBeacon = true
            intervalMillis = 2_000
            batteryLevel = 12
            silentAtT = 0.35
        }

        // 3. a lone node elsewhere, critical battery, goes dark -> EXPECTED_SILENCE (T4)
        nodes.first { it.cluster != CLUSTER_COUNT && it.silentAtT == null }.apply {
            fastBeacon = true
            intervalMillis = 2_000
            batteryLevel = 1
            silentAtT = 0.42
        }
    }

    private fun gaussian(scale: Int): Int =
        ((rng.nextDouble() + rng.nextDouble() + rng.nextDouble() - 1.5) * scale).roundToInt()

    class SimNode(
        private val identity: DeviceIdentity,
        var deltaLat: Int,
        val deltaLon: Int,
        val cluster: Int,
        val severeAtT: Double,
        private val baseSeverity: Int,
        private val severeSeverity: Int,
        var batteryLevel: Int,
        val rssi: Int,
        var fastBeacon: Boolean,
        var intervalMillis: Long,
        var silentAtT: Double?,
        val startOffsetMillis: Long,
    ) {
        var nextEmitAtMillis: Long = 0L

        fun emit(t: Float, nowMillis: Long): ByteArray? {
            silentAtT?.let { if (t >= it) return null }

            val severe = t >= severeAtT
            val severity = if (severe) severeSeverity else baseSeverity
            val status = when {
                batteryLevel <= 1 -> Status.UNKNOWN.code
                severe && severity >= 12 -> Status.TRAPPED_DEBRIS.code
                severe -> Status.INJURED.code
                else -> Status.UNKNOWN.code
            }
            val nextTx = if (fastBeacon) 2 else (intervalMillis / 1000).toInt()

            val packet = Packet(
                version = Packet.PROTOCOL_VERSION,
                type = Packet.TYPE_STATUS,
                deviceId = identity.deviceId,
                deltaLat = deltaLat,
                deltaLon = deltaLon,
                status = status,
                severity = severity,
                casualties = if (severe) (severity - 8).coerceIn(0, 15) else 0,
                timestampMinutes = EventClock.stampMinutes(nowMillis),
                batteryLevel = batteryLevel.coerceIn(0, 15),
                hopCount = 0,
                nextExpectedTxSeconds = nextTx.coerceAtLeast(1),
                altDelta = Packet.NO_BAROMETER,
                altTrend = 0,
            )
            return PacketCodec.encode(packet, identity)
        }
    }

    companion object {
        private const val TICK_MS = 400L
        private const val CLUSTER_COUNT = 9
        private const val SPREAD = 70 // ~75 m gaussian spread within a cluster
        private const val DOOMED_COUNT = 12

        /**
         * Real Rasuwagadhi toll over 72 h, as a fraction of the final 626,
         * against normalised scenario time. Piecewise-linear, monotonic.
         */
        private val TOLL_KEYFRAMES = listOf(
            0.00 to 22.0 / 626,
            0.33 to 95.0 / 626,
            0.66 to 469.0 / 626,
            1.00 to 626.0 / 626,
        )

        fun tollFraction(t: Double): Double {
            val tt = t.coerceIn(0.0, 1.0)
            for (i in 1 until TOLL_KEYFRAMES.size) {
                val (t0, f0) = TOLL_KEYFRAMES[i - 1]
                val (t1, f1) = TOLL_KEYFRAMES[i]
                if (tt <= t1) return f0 + (f1 - f0) * ((tt - t0) / (t1 - t0))
            }
            return 1.0
        }

        private fun tSuchThatTollFractionReaches(fraction: Double): Double {
            val f = fraction.coerceIn(0.0, 1.0)
            for (i in 1 until TOLL_KEYFRAMES.size) {
                val (t0, f0) = TOLL_KEYFRAMES[i - 1]
                val (t1, f1) = TOLL_KEYFRAMES[i]
                if (f <= f1) return t0 + (t1 - t0) * ((f - f0) / (f1 - f0)).coerceIn(0.0, 1.0)
            }
            return 1.0
        }
    }
}
