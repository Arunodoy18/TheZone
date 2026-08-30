package com.thezone.packet

/**
 * The shared "event epoch" the 2-byte `timestamp` field counts minutes from
 * (PACKET_SPEC "timestamp"). Like the position origin, it is a per-deployment
 * constant compiled into every phone's build so stamps are comparable across
 * devices — which is what lets a receiver tell a fresh heartbeat from a stale
 * relayed copy in Dead Man's Packet (PRD §4).
 */
object EventClock {

    // TODO(H8): set to the actual disaster / demo start before staging.
    // Placeholder: 2026-08-30T00:00:00Z.
    const val EVENT_EPOCH_MILLIS = 1_788_048_000_000L

    private const val MINUTE_MS = 60_000L

    /** uint16 range — the field wraps every ~45.5 days. */
    const val WRAP_MINUTES = 65_536
    private const val WRAP_MS = WRAP_MINUTES * MINUTE_MS

    /** Minutes since the epoch, wrapped into the uint16 field. */
    fun stampMinutes(nowMillis: Long): Int =
        ((nowMillis - EVENT_EPOCH_MILLIS) / MINUTE_MS).mod(WRAP_MINUTES.toLong()).toInt()

    /**
     * Reconstruct the wall-clock instant a [stampMinutes] value refers to,
     * choosing the wrap era closest to [referenceMillis] (normally the receive
     * time).
     */
    fun sentAtMillis(stampMinutes: Int, referenceMillis: Long): Long {
        var t = EVENT_EPOCH_MILLIS + stampMinutes.toLong() * MINUTE_MS
        while (referenceMillis - t > WRAP_MS / 2) t += WRAP_MS
        while (t - referenceMillis > WRAP_MS / 2) t -= WRAP_MS
        return t
    }
}
