package com.thezone.demo

/**
 * Stage knobs. BUILD_PLAN: "you're demoing a battery-adaptive system on full
 * batteries — you'll need to fake the battery level to show the ladder. Add a
 * hidden debug gesture to override reported battery. Do this in H4, not on stage."
 *
 * This is the mechanism. The debug screen sets it now; H6's Citizen screen wires
 * it to a hidden gesture (e.g. a long-press in a dead corner).
 */
object DebugOverrides {

    /** When non-null, the heartbeat reports this instead of the real battery %. */
    @Volatile
    var batteryPercentOverride: Int? = null
        set(value) {
            field = value?.coerceIn(0, 100)
        }
}

/**
 * The Citizen screen's three optional buttons (Trapped / Water rising / Safe).
 * Null = no user assertion; the heartbeat then reports sensor-derived status.
 */
object UserStatus {
    @Volatile
    var code: Int? = null
}

