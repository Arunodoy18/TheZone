package com.thezone.core

import com.thezone.packet.Packet
import com.thezone.packet.Status

/**
 * Confidence scoring for the severity map (PS5 — "fragmented, contradictory,
 * unverified reports"). The map is *ranked, not decided*: humans still allocate
 * the boats. This just says how much to trust each cell's picture, from signals
 * that are hard to fake:
 *
 *  - independent devices — distinct `device_id`s reporting the same cell
 *  - path diversity — a report that reached us at more than one hop count came
 *    down more than one route
 *  - physical plausibility — the altitude / trend / status don't contradict
 *  - a verified reporter — `Status.RESPONDER` in the cell (pre-registered key)
 *
 * A single panicking phone lands near 0. Three independent phones plus a
 * responder lands near 1. Pure Kotlin, unit-tested.
 */
object CorroborationScorer {

    private const val W_DEVICES = 0.35
    private const val W_PATHS = 0.15
    private const val W_PLAUSIBLE = 0.25
    private const val W_VERIFIED = 0.25

    /** Distinct devices needed before the device term saturates. */
    private const val DEVICES_FOR_FULL = 3

    fun scoreCells(reports: List<StoredReport>): List<CellConfidence> {
        data class R(val dev: String, val report: StoredReport)

        val byCell = reports.asSequence()
            .filterNot { it.isOwn }
            .map { r ->
                val dev = r.packet.deviceId.joinToString("") { "%02x".format(it) }
                val cell = GridCells.of(r.packet.deltaLat, r.packet.deltaLon) ?: GridCells.fallback(dev)
                cell to R(dev, r)
            }
            .groupBy({ it.first }, { it.second })

        return byCell.map { (cell, list) ->
            val devices = list.map { it.dev }.toSet()
            val inCell = list.map { it.report }
            val nonSafe = inCell.filter { it.packet.status != Status.SAFE.code }
            val severity = nonSafe.maxOfOrNull { it.packet.severity } ?: 0
            val pathDiversity = inCell.maxOfOrNull { it.hopsSeen.size } ?: 1
            val hasVerified = inCell.any { it.packet.status == Status.RESPONDER.code }
            val implausible = inCell.count { !plausible(it.packet) }
            val plausibleFrac =
                if (inCell.isEmpty()) 0.0 else (inCell.size - implausible).toDouble() / inCell.size

            val confidence = (
                W_DEVICES * unit(devices.size.toDouble() / DEVICES_FOR_FULL) +
                    W_PATHS * unit((pathDiversity - 1).toDouble()) + // 1 route -> 0, 2+ -> 1
                    W_PLAUSIBLE * plausibleFrac +
                    W_VERIFIED * (if (hasVerified) 1.0 else 0.0)
                ).coerceIn(0.0, 1.0)

            CellConfidence(
                cell = cell,
                severity = severity,
                confidence = confidence,
                distinctDevices = devices.size,
                pathDiversity = pathDiversity,
                hasVerifiedReporter = hasVerified,
                implausibleReports = implausible,
            )
        }
    }

    /** Does the report contradict itself physically? */
    fun plausible(p: Packet): Boolean {
        // altitude pinned at the clamp = a bad / spoofed sensor value
        if (p.altDelta != Packet.NO_BAROMETER && (p.altDelta <= -127 || p.altDelta >= 127)) return false
        // "water rising" while altitude is dropping hard
        if (p.status == Status.RISING_WATER.code && p.altTrend <= -3) return false
        // "trapped under debris" from well above grade
        if (p.status == Status.TRAPPED_DEBRIS.code &&
            p.altDelta != Packet.NO_BAROMETER && p.altDelta > 30
        ) return false
        if (p.hopCount >= Packet.MAX_HOPS) return false
        return true
    }

    private fun unit(x: Double) = x.coerceIn(0.0, 1.0)
}

data class CellConfidence(
    val cell: GridCell,
    val severity: Int,
    /** 0..1 — how much to trust this cell's severity picture. */
    val confidence: Double,
    val distinctDevices: Int,
    val pathDiversity: Int,
    val hasVerifiedReporter: Boolean,
    val implausibleReports: Int,
)
