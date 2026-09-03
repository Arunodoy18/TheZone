package com.thezone.config

import android.content.Context
import com.thezone.packet.GeoPosition

/**
 * Per-incident settings that every phone in one deployment must agree on. Right
 * now that's just the **position origin** — packet positions are deltas from it
 * (docs/PACKET_SPEC.md "Position encoding"), so two phones with different origins
 * produce deltas that don't line up on the map.
 *
 * It is deliberately *not* auto-set from the first GPS fix — that would give
 * every phone a different origin. Set it once at the staging point (same value
 * keyed / scanned onto every phone) or leave the built-in default.
 *
 * Android-only (SharedPreferences); the math stays in [GeoPosition].
 */
object IncidentConfig {

    private const val PREFS = "thezone_incident"
    private const val K_LAT = "origin_lat"
    private const val K_LON = "origin_lon"

    @Volatile private var latCache: Double? = null
    @Volatile private var lonCache: Double? = null

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun originLat(context: Context): Double =
        latCache ?: prefs(context).let {
            (if (it.contains(K_LAT)) it.getFloat(K_LAT, 0f).toDouble() else GeoPosition.ORIGIN_LAT)
                .also { v -> latCache = v }
        }

    fun originLon(context: Context): Double =
        lonCache ?: prefs(context).let {
            (if (it.contains(K_LON)) it.getFloat(K_LON, 0f).toDouble() else GeoPosition.ORIGIN_LON)
                .also { v -> lonCache = v }
        }

    /** True while the origin is still the compiled-in default (nothing set for this incident). */
    fun usingDefault(context: Context): Boolean = !prefs(context).contains(K_LAT)

    fun setOrigin(context: Context, lat: Double, lon: Double) {
        prefs(context).edit()
            .putFloat(K_LAT, lat.toFloat())
            .putFloat(K_LON, lon.toFloat())
            .apply()
        latCache = lat
        lonCache = lon
    }

    fun reset(context: Context) {
        prefs(context).edit().remove(K_LAT).remove(K_LON).apply()
        latCache = null
        lonCache = null
    }
}
