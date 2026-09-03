package com.thezone.config

import android.content.Context
import android.util.Base64
import com.thezone.packet.DeviceIdentity
import com.thezone.packet.GeoPosition
import java.security.SecureRandom

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
    private const val K_RESPONDER_KEY = "responder_key_b64"

    /** Shared responder secret length. Any length works for the MAC; 16 B is plenty. */
    const val RESPONDER_KEY_BYTES = 16

    @Volatile private var latCache: Double? = null
    @Volatile private var lonCache: Double? = null
    @Volatile private var respKeyCache: ByteArray? = null
    @Volatile private var respKeyLoaded = false

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

    // --- pre-shared responder key ---------------------------------------------

    /** The shared responder secret, or null if this phone isn't provisioned as a responder. */
    fun responderKey(context: Context): ByteArray? {
        if (respKeyLoaded) return respKeyCache
        synchronized(this) {
            if (respKeyLoaded) return respKeyCache
            val b64 = prefs(context).getString(K_RESPONDER_KEY, null)
            respKeyCache = b64?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
            respKeyLoaded = true
            return respKeyCache
        }
    }

    fun hasResponderKey(context: Context): Boolean = responderKey(context) != null

    fun setResponderKey(context: Context, key: ByteArray?) {
        val e = prefs(context).edit()
        if (key == null || key.isEmpty()) e.remove(K_RESPONDER_KEY) else
            e.putString(K_RESPONDER_KEY, Base64.encodeToString(key, Base64.NO_WRAP))
        e.apply()
        respKeyCache = key?.copyOf()
        respKeyLoaded = true
    }

    /** A short public fingerprint so two phones can confirm they hold the same key. */
    fun responderKeyFingerprint(context: Context): String? =
        responderKey(context)?.let { k ->
            DeviceIdentity.sha256(k, ByteArray(0)).take(4).joinToString("") { "%02x".format(it) }
        }

    fun newResponderKey(): ByteArray =
        ByteArray(RESPONDER_KEY_BYTES).also { SecureRandom().nextBytes(it) }

    fun parseKeyHex(hex: String): ByteArray? {
        val clean = hex.trim().replace(" ", "").replace(":", "")
        if (clean.isEmpty() || clean.length % 2 != 0) return null
        return runCatching {
            ByteArray(clean.length / 2) { clean.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }.getOrNull()
    }

    fun keyToHex(key: ByteArray): String = key.joinToString("") { "%02x".format(it) }
}
