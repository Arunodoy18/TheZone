package com.thezone.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Brand-neutral palette derived from the subject: a sonar / topographic display.
 * Near-black ground, one restrained signal accent, a desaturated severity ramp.
 * Deliberately not gradient-on-dark-with-an-acid-accent.
 */
object Zone {

    // Dark surfaces (Citizen, Map)
    val ink = Color(0xFF0A0E12)
    val inkSoft = Color(0xFF141B22)
    val inkLine = Color(0xFF263039)

    // Text on dark
    val bone = Color(0xFFE9E4D8)
    val boneDim = Color(0xFF8C949E)

    // The one accent — "you are heard"
    val signal = Color(0xFF19E3C3)

    // Severity ramp / alerts
    val calm = Color(0xFF2E7D8A)
    val amber = Color(0xFFF2A93B)
    val alarm = Color(0xFFE4572E)

    // Daylight surfaces (Responder — gloves, sun, one hand)
    val paper = Color(0xFFF3F0E8)
    val paperInk = Color(0xFF14181C)
    val paperLine = Color(0xFFCBC6B8)

    /** Severity 0..15 → colour. Low = calm teal, mid = amber, high = signal red. */
    fun severity(sev0to15: Int): Color {
        val t = (sev0to15.coerceIn(0, 15)) / 15f
        return if (t < 0.5f) lerp(calm, amber, t / 0.5f)
        else lerp(amber, alarm, (t - 0.5f) / 0.5f)
    }
}
