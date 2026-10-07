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

/**
 * Optional self-reported detail from the Citizen screen. [headcount] feeds the
 * packet's casualty nibble (0..15, 15 = "15 or more"); 0 = not stated. Triage
 * uses it to break ties toward cells with more people.
 */
object SelfReport {
    @Volatile
    var headcount: Int = 0
        set(value) {
            field = value.coerceIn(0, 15)
        }

    /**
     * One optional phrase from [com.thezone.packet.StatusPhrases], picked on the
     * Citizen screen. Null = none (the packet's reserved[24] byte stays 0, exactly
     * as every report before this field existed). 1 byte on the wire — not free
     * text, see docs/PACKET_SPEC.md "STATUS phrase code".
     */
    @Volatile
    var phraseCode: Int? = null

    /**
     * How urgent this report is, 0 (not stated) .. 15 (critical) — the packet's
     * severity nibble, set on the Citizen screen. Was hardcoded to 0 for every
     * report this app ever sent until this field existed; triage sort and the
     * map's colour grid both key off it, so leaving it at 0 silently flattened
     * every report to the same priority.
     */
    @Volatile
    var severity: Int = 0
        set(value) {
            field = value.coerceIn(0, 15)
        }
}

