package com.thezone.core

import com.thezone.packet.BatteryScale
import com.thezone.packet.EventClock
import com.thezone.packet.Packet

/**
 * Dead Man's Packet — the USP (PRD §4). For every device we've heard, decide on a
 * timer tick whether its silence is expected (it told us it was about to go
 * quiet) or unexpected (it promised to speak and vanished). Then the cell-level
 * payoff: many devices in one grid cell going unexpectedly silent together means
 * the area was destroyed.
 *
 * Every state transition is recorded with a wall-clock timestamp so the demo can
 * point at the exact moment a device went dark.
 *
 * Pure Kotlin. Zero Android imports. Unit-tested on the JVM.
 */
class SilenceEvaluator(
    /** `grace = max(graceFloor, graceMultiplier * promised)` (PRD: start at 2x). */
    private val graceMultiplier: Double = 2.0,
    private val graceFloorMillis: Long = 8_000L,
    /**
     * UNEXPECTED once `overdue_by > unexpectedMultiplier * promised`, where
     * `overdue_by = now - (last_heard_at + promised)` (PRD §4 pseudocode, so the
     * total silence is ~4x the promised interval at the default 3.0). Drop to 2.0
     * to match BUILD_PLAN's looser "within 3x its declared interval"; tune on real
     * phones.
     */
    private val unexpectedMultiplier: Double = 3.0,
    /** `battery <= this` at silence time -> EXPECTED_SILENCE, never escalates. */
    private val criticalBatteryPercent: Int = 10,
    private val cellMinDevices: Int = 3,
    private val cellSilentFraction: Double = 0.80,
    private val cellWindowMillis: Long = 120_000L,
    /** Grid cell edge in position units (~1.1 m each); 90 ≈ 100 m. */
    private val cellSizeUnits: Int = 90,
    /**
     * A packet whose `timestamp` says it was sent longer ago than this is a relay
     * echo of an old heartbeat — it stays in the store as data but tells us
     * nothing new about the sender being alive *now*. Must exceed the 60 s
     * quantization of the minute stamp.
     */
    private val freshnessWindowMillis: Long = 90_000L,
    private val maxTransitionLog: Int = 200,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    private val lock = Any()
    private val tracks = LinkedHashMap<String, Track>()
    private val transitionLog = ArrayDeque<SilenceTransition>()
    private val flaggedCells = LinkedHashMap<GridCell, CellLoss>()

    private class Track(
        var lastHeardAtMillis: Long,
        var promisedNextTxSeconds: Int,
        var lastBatteryPercent: Int,
        var state: SilenceState,
        var stateSinceMillis: Long,
        var unexpectedSinceMillis: Long?,
        var cell: GridCell?,
    )

    /**
     * Fold in a heard packet. `last_heard_at` is the fine-grained *reception*
     * time; a copy whose stamp says it was sent more than [freshnessWindowMillis]
     * ago is treated as a stale relay and ignored for liveness (it is still held
     * as data by the store).
     */
    fun onPacket(deviceIdHex: String, packet: Packet, receivedAtMillis: Long = nowMillis()) {
        synchronized(lock) {
            val sentAt = EventClock.sentAtMillis(packet.timestampMinutes, receivedAtMillis)
                .coerceAtMost(receivedAtMillis)
            if (receivedAtMillis - sentAt > freshnessWindowMillis) return // stale relay echo

            val battery = BatteryScale.nibbleToPercent(packet.batteryLevel)
            val promised = packet.nextExpectedTxSeconds.coerceAtLeast(1)
            val cell = cellFor(packet)

            val track = tracks[deviceIdHex]
            if (track == null) {
                tracks[deviceIdHex] = Track(
                    lastHeardAtMillis = receivedAtMillis,
                    promisedNextTxSeconds = promised,
                    lastBatteryPercent = battery,
                    state = SilenceState.ALIVE,
                    stateSinceMillis = receivedAtMillis,
                    unexpectedSinceMillis = null,
                    cell = cell,
                )
                return
            }
            if (receivedAtMillis >= track.lastHeardAtMillis) {
                track.lastHeardAtMillis = receivedAtMillis
                track.promisedNextTxSeconds = promised
                track.lastBatteryPercent = battery
            }
            if (cell != null) track.cell = cell
            reclassify(deviceIdHex, track, receivedAtMillis) // catch return-from-dead immediately
        }
    }

    /** Reclassify every device and run cell-loss detection. */
    fun tick(now: Long = nowMillis()): TickResult {
        synchronized(lock) {
            val transitions = ArrayList<SilenceTransition>()
            for ((id, track) in tracks) reclassify(id, track, now)?.let(transitions::add)
            val losses = detectCellLoss(now)
            return TickResult(transitions, losses)
        }
    }

    fun snapshot(now: Long = nowMillis()): List<DeviceSilence> = synchronized(lock) {
        tracks.map { (id, t) ->
            DeviceSilence(
                deviceIdHex = id,
                state = t.state,
                lastHeardAtMillis = t.lastHeardAtMillis,
                promisedNextTxSeconds = t.promisedNextTxSeconds,
                lastBatteryPercent = t.lastBatteryPercent,
                consecutiveMisses = missesFor(t, now),
                cell = t.cell,
                unexpectedSinceMillis = t.unexpectedSinceMillis,
            )
        }
    }

    fun transitions(): List<SilenceTransition> = synchronized(lock) { transitionLog.toList() }

    fun cellLosses(): List<CellLoss> = synchronized(lock) { flaggedCells.values.toList() }

    fun deviceState(deviceIdHex: String): SilenceState? =
        synchronized(lock) { tracks[deviceIdHex]?.state }

    fun clear() = synchronized(lock) {
        tracks.clear()
        transitionLog.clear()
        flaggedCells.clear()
    }

    // --- internals ------------------------------------------------------

    /** Caller holds [lock]. Returns a transition iff the state changed. */
    private fun reclassify(id: String, t: Track, now: Long): SilenceTransition? {
        val promisedMs = t.promisedNextTxSeconds * 1000L
        val grace = maxOf(graceFloorMillis, (promisedMs * graceMultiplier).toLong())
        val overdueBy = now - (t.lastHeardAtMillis + promisedMs)

        val next = when {
            overdueBy < grace -> SilenceState.ALIVE
            t.lastBatteryPercent <= criticalBatteryPercent -> SilenceState.EXPECTED_SILENCE
            overdueBy > (promisedMs * unexpectedMultiplier).toLong() -> SilenceState.UNEXPECTED_SILENCE
            else -> SilenceState.OVERDUE
        }
        if (next == t.state) return null

        val from = t.state
        t.state = next
        t.stateSinceMillis = now
        t.unexpectedSinceMillis =
            if (next == SilenceState.UNEXPECTED_SILENCE) now else null

        val transition = SilenceTransition(
            deviceIdHex = id,
            from = from,
            to = next,
            atMillis = now,
            sinceLastHeardMillis = now - t.lastHeardAtMillis,
            promisedNextTxSeconds = t.promisedNextTxSeconds,
            batteryPercent = t.lastBatteryPercent,
        )
        transitionLog.addLast(transition)
        while (transitionLog.size > maxTransitionLog) transitionLog.removeFirst()
        return transition
    }

    /** Caller holds [lock]. One [CellLoss] per cell, emitted once. */
    private fun detectCellLoss(now: Long): List<CellLoss> {
        val fresh = ArrayList<CellLoss>()
        val byCell = tracks.values.filter { it.cell != null }.groupBy { it.cell!! }
        for ((cell, members) in byCell) {
            if (cell in flaggedCells) continue
            if (members.size < cellMinDevices) continue
            val silentAt = members.mapNotNull { it.unexpectedSinceMillis }
            if (silentAt.size < cellMinDevices) continue
            if (silentAt.size < cellSilentFraction * members.size) continue
            val first = silentAt.min()
            val last = silentAt.max()
            if (last - first > cellWindowMillis) continue

            val loss = CellLoss(
                cell = cell,
                deviceCount = members.size,
                silentCount = silentAt.size,
                firstSilentAtMillis = first,
                lastSilentAtMillis = last,
                detectedAtMillis = now,
            )
            flaggedCells[cell] = loss
            fresh.add(loss)
        }
        return fresh
    }

    private fun cellFor(packet: Packet): GridCell? {
        if (!packet.hasFix()) return null
        return GridCell(
            Math.floorDiv(packet.deltaLat, cellSizeUnits),
            Math.floorDiv(packet.deltaLon, cellSizeUnits),
        )
    }

    private fun missesFor(t: Track, now: Long): Int {
        val promisedMs = t.promisedNextTxSeconds * 1000L
        if (promisedMs <= 0L) return 0
        val overdueBy = now - (t.lastHeardAtMillis + promisedMs)
        return if (overdueBy <= 0L) 0 else (overdueBy / promisedMs).toInt()
    }
}

enum class SilenceState {
    /** Heard within grace. */
    ALIVE,

    /** Past grace but not yet declared dead, and battery isn't critical. */
    OVERDUE,

    /** Silent, but it was on critical battery — we expected this. Deprioritise. */
    EXPECTED_SILENCE,

    /** Promised to speak and didn't, with battery to spare. Escalate. */
    UNEXPECTED_SILENCE,
}

data class GridCell(val latIndex: Int, val lonIndex: Int)

data class DeviceSilence(
    val deviceIdHex: String,
    val state: SilenceState,
    val lastHeardAtMillis: Long,
    val promisedNextTxSeconds: Int,
    val lastBatteryPercent: Int,
    val consecutiveMisses: Int,
    val cell: GridCell?,
    val unexpectedSinceMillis: Long?,
)

data class SilenceTransition(
    val deviceIdHex: String,
    val from: SilenceState,
    val to: SilenceState,
    /** Wall-clock ms — the moment to point at on stage. */
    val atMillis: Long,
    val sinceLastHeardMillis: Long,
    val promisedNextTxSeconds: Int,
    val batteryPercent: Int,
)

data class CellLoss(
    val cell: GridCell,
    val deviceCount: Int,
    val silentCount: Int,
    val firstSilentAtMillis: Long,
    val lastSilentAtMillis: Long,
    val detectedAtMillis: Long,
)

data class TickResult(
    val transitions: List<SilenceTransition>,
    val newCellLosses: List<CellLoss>,
)
