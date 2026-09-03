package com.thezone.core

import com.thezone.packet.Packet
import com.thezone.packet.Status

/**
 * Confidence scoring for the severity map (PS5 — "fragmented, contradictory,
 * unverified reports"). The map is *ranked, not decided*: humans still allocate
 * the boats. This just says how much to trust each cell's picture, from signals
 * that are hard to fake:
 *
 *  - independent devices — distinct `device_id`s reporting the same cell,
 *    counting only physically-plausible reports so a flood of junk packets from
 *    fabricated ids can't pad the count (Sybil resistance)
 *  - path diversity — a report that reached us at more than one hop count came
 *    down more than one route
 *  - physical plausibility — the altitude / trend / status don't contradict
 *  - a verified reporter — a plausible `Status.RESPONDER` in the cell whose
 *    device id is in [verifiedResponders] (its `auth` checked out against the
 *    pre-shared responder key). A RESPONDER claim that *doesn't* verify counts
 *    for nothing extra — it earns trust only in proportion to independent
 *    corroboration, like any other phone.
 *
 * A single panicking phone lands near 0. Three independent phones plus a
 * verified responder lands near 1. Pure Kotlin, unit-tested.
 */
object CorroborationScorer {

    private const val W_DEVICES = 0.35
    private const val W_PATHS = 0.15
    private const val W_PLAUSIBLE = 0.25
    private const val W_VERIFIED = 0.25

    /** Distinct devices needed before the device term saturates. */
    private const val DEVICES_FOR_FULL = 3

    fun scoreCells(
        reports: List<StoredReport>,
        /** device_id hex of reports whose `auth` verified against the responder key. */
        verifiedResponders: Set<String> = emptySet(),
    ): List<CellConfidence> {
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
            val implausible = inCell.count { !plausible(it.packet) }
            val plausibleFrac =
                if (inCell.isEmpty()) 0.0 else (inCell.size - implausible).toDouble() / inCell.size

            // only plausible reports feed the device-count term — a Sybil flood of
            // junk packets from fabricated ids can't pad it
            val credibleDevices = list.filter { plausible(it.report.packet) }.map { it.dev }.toSet().size

            // a cryptographically-verified responder (auth checked against the
            // shared key) in the cell earns the full verified weight
            val hasVerified = list.any {
                it.dev in verifiedResponders &&
                    it.report.packet.status == Status.RESPONDER.code &&
                    plausible(it.report.packet)
            }

            val confidence = (
                W_DEVICES * unit(credibleDevices.toDouble() / DEVICES_FOR_FULL) +
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
