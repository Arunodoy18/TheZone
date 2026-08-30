package com.thezone.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily

/**
 * Brand-neutral palette derived from the subject: a sonar / bathymetric display.
 * Near-black ground, one phosphor accent, a severity ramp that reads like
 * elevation gaining toward danger. Hairline rules stand in for contour / range
 * rings. Not gradient-on-dark-with-an-acid-accent.
 */
object Zone {

    // --- dark surfaces: Citizen, Map ---
    val ink = Color(0xFF070A0D)          // near-black ground
    val inkSoft = Color(0xFF10161C)      // panel
    val inkLine = Color(0xFF223038)      // hairline / contour

    val bone = Color(0xFFEDE8DA)         // primary text on dark
    val boneDim = Color(0xFF7C8792)      // secondary text
    val boneFaint = Color(0xFF3B454E)    // tertiary / disabled

    /** the one accent — "you are heard" / live contact */
    val signal = Color(0xFF1BE7C4)

    // --- severity ramp: calm water -> shoal -> alarm ---
    val calm = Color(0xFF1F6E7B)
    val shoal = Color(0xFF3FA7A0)
    val amber = Color(0xFFF2A93B)
    val alarm = Color(0xFFEF5B32)
    val alarmDeep = Color(0xFFB5311B)

    val sans: FontFamily = PlexSans
    val mono: FontFamily = PlexMono

    // --- daylight surfaces: Responder (gloves, sun, one hand) ---
    val paper = Color(0xFFF6F3EC)
    val paperPanel = Color(0xFFFFFFFF)
    val paperInk = Color(0xFF0E1114)     // max-contrast text
    val paperDim = Color(0xFF4A5157)
    val paperLine = Color(0xFFD8D2C4)

    /** Severity 0..15 -> colour on the calm→shoal→amber→alarm ramp. */
    fun severity(sev0to15: Int): Color {
        val t = sev0to15.coerceIn(0, 15) / 15f
        return when {
            t < 0.34f -> lerp(calm, shoal, t / 0.34f)
            t < 0.67f -> lerp(shoal, amber, (t - 0.34f) / 0.33f)
            else -> lerp(amber, alarm, (t - 0.67f) / 0.33f)
        }
    }
}
