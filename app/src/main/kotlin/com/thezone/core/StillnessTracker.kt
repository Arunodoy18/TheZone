package com.thezone.core

/**
 * Sustained-immobility detector for the "works when the person is unconscious or
 * the phone is buried" claim (PACKET_SPEC status enum: TRAPPED_DEBRIS is
 * user-asserted *or inferred from immobility*). Fed a stream of linear-ish
 * acceleration magnitudes; reports how long the phone has been essentially still.
 *
 * Pure Kotlin. Zero Android imports. Unit-tested on the JVM.
 */
class StillnessTracker(
    /** m/s² of motion above which the phone counts as "being handled / carried". */
    private val moveThresholdMps2: Double = 0.8,
    /** How long continuously still before [isStill] flips true. */
    private val stillAfterMillis: Long = 5L * 60 * 1000,
) {

    private var lastMotionAtMillis: Long = 0L
    private var seeded = false

    /** Fold one sample. [magnitude] is |linear acceleration| (gravity removed). */
    fun onSample(magnitude: Double, atMillis: Long) {
        if (!seeded) {
            lastMotionAtMillis = atMillis
            seeded = true
            return
        }
        if (magnitude > moveThresholdMps2) lastMotionAtMillis = atMillis
    }

    /** The moment the phone was last moved — it has been still since then. Null until seeded. */
    fun stillSinceMillis(): Long? = if (seeded) lastMotionAtMillis else null

    fun isStill(now: Long): Boolean = seeded && now - lastMotionAtMillis >= stillAfterMillis

    fun reset() {
        seeded = false
        lastMotionAtMillis = 0L
    }
}
