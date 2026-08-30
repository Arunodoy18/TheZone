package com.thezone.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.thezone.probe.R

/**
 * IBM Plex — an engineered typeface family, bundled as `res/font` so it works in
 * airplane mode like everything else. Sans for UI, Mono for every readout and
 * coordinate.
 */
val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_bold, FontWeight.Bold),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
)

/** Every unstyled [androidx.compose.material3.Text] inherits Plex Sans. */
fun zoneTypography(): Typography {
    val base = Typography()
    fun TextStyle.plex() = copy(fontFamily = PlexSans)
    return base.copy(
        displayLarge = base.displayLarge.plex(), displayMedium = base.displayMedium.plex(),
        displaySmall = base.displaySmall.plex(),
        headlineLarge = base.headlineLarge.plex(), headlineMedium = base.headlineMedium.plex(),
        headlineSmall = base.headlineSmall.plex(),
        titleLarge = base.titleLarge.plex(), titleMedium = base.titleMedium.plex(),
        titleSmall = base.titleSmall.plex(),
        bodyLarge = base.bodyLarge.plex(), bodyMedium = base.bodyMedium.plex(),
        bodySmall = base.bodySmall.plex(),
        labelLarge = base.labelLarge.plex(), labelMedium = base.labelMedium.plex(),
        labelSmall = base.labelSmall.plex(),
    )
}
