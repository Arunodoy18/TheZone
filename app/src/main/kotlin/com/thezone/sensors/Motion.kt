package com.thezone.sensors

import com.thezone.core.StillnessTracker

/**
 * Latest motion state, best-effort. Mirrors [Altitude] / [Position]: a plain
 * singleton the heartbeat reads so nothing downstream imports Android sensor
 * types. Backed by a pure [StillnessTracker].
 */
object Motion {

    @Volatile private var tracker: StillnessTracker? = null
    @Volatile var hasAccelerometer: Boolean = false
        private set

    fun onSample(magnitude: Double, atMillis: Long) {
        (tracker ?: StillnessTracker().also { tracker = it }).onSample(magnitude, atMillis)
    }

    fun markNoAccelerometer() {
        hasAccelerometer = false
        tracker = null
    }

    fun markHasAccelerometer() {
        hasAccelerometer = true
    }

    /** True once the phone has been essentially still long enough to infer TRAPPED. */
    fun isStill(now: Long = System.currentTimeMillis()): Boolean =
        tracker?.isStill(now) == true

    fun stillSinceMillis(): Long? = tracker?.stillSinceMillis()

    fun clear() {
        tracker = null
    }
}
