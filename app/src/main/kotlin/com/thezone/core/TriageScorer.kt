package com.thezone.core

import com.thezone.packet.Packet
import com.thezone.packet.Status

/** A responder-list row: a stored report joined with its Dead Man's Packet state. */
data class TriageEntry(
    val deviceIdHex: String,
    val status: Int,
    val severity: Int,
    val casualties: Int,
    /** metres vs baseline, or [Packet.NO_BAROMETER]. */
    val altDelta: Int,
    /** metres change across the last 3 transmissions. */
    val altTrend: Int,
    val batteryPercent: Int,
    val hopsFromOrigin: Int,
    val lastHeardAtMillis: Long,
    val lastRssiDbm: Int,
    val silence: SilenceState,
    val unexpectedSinceMillis: Long?,
)

/**
 * Triage priority (PRD §5). The responder list sorts by a single computed rank,
 * not by time. Rank order (most urgent first):
 *
 *  1. RISING_WATER with a rising altitude trend — climbing as water rises
 *  2. UNEXPECTED_SILENCE within the last 5 minutes — just lost, most likely alive
 *  3. TRAPPED_DEBRIS below grade (negative altitude) — crush injury
 *  4. Any status on critical battery — about to stop transmitting
 *  5. Everything else, by severity then recency
 *
 * Point 4 is the one worth saying on stage: prioritising by *who is about to go
 * silent* is a triage signal that falls straight out of the USP.
 *
 * Pure Kotlin. Zero Android imports.
 */
object TriageScorer {

    const val CRITICAL_BATTERY_PERCENT = 10
    const val JUST_LOST_WINDOW_MILLIS = 5 * 60_000L

    /** 5 = most urgent (PRD rank 1) … 1 = "everything else" (PRD rank 5). */
    fun tier(e: TriageEntry, now: Long): Int = when {
        e.status == Status.RISING_WATER.code && e.altTrend > 0 -> 5

        e.silence == SilenceState.UNEXPECTED_SILENCE &&
            e.unexpectedSinceMillis != null &&
            now - e.unexpectedSinceMillis <= JUST_LOST_WINDOW_MILLIS -> 4

        e.status == Status.TRAPPED_DEBRIS.code &&
            e.altDelta != Packet.NO_BAROMETER && e.altDelta < 0 -> 3

        e.batteryPercent in 0 until CRITICAL_BATTERY_PERCENT -> 2

        else -> 1
    }

    private val comparator = { now: Long ->
        compareByDescending<TriageEntry> { tier(it, now) }
            .thenByDescending { it.severity }
            .thenByDescending { it.lastHeardAtMillis }
    }

    fun sort(entries: List<TriageEntry>, now: Long): List<TriageEntry> =
        entries.sortedWith(comparator(now))

    /** A short human label for why a row sits where it does. */
    fun reason(e: TriageEntry, now: Long): String = when (tier(e, now)) {
        5 -> "rising water, climbing"
        4 -> "just went silent"
        3 -> "trapped, below grade"
        2 -> "battery critical"
        else -> "severity ${e.severity}"
    }
}
