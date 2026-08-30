package com.thezone.identity

import android.content.Context
import android.util.Base64
import com.thezone.packet.DeviceIdentity
import java.security.SecureRandom

/**
 * Persists the per-install 32-byte key that backs [DeviceIdentity]. Generated
 * once on first access and kept in private SharedPreferences thereafter — this is
 * the whole identity story for the demo (PRD.md §2).
 *
 * Android-only (SharedPreferences), so it lives outside `packet/` and `core/`,
 * both of which stay JVM-testable.
 */
object DeviceKeyStore {

    private const val PREFS = "thezone_identity"
    private const val KEY_B64 = "device_key_b64"

    @Volatile
    private var cached: DeviceIdentity? = null

    fun identity(context: Context): DeviceIdentity {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val existing = prefs.getString(KEY_B64, null)
            val key = if (existing != null) {
                Base64.decode(existing, Base64.NO_WRAP)
            } else {
                ByteArray(DeviceIdentity.KEY_BYTES).also { SecureRandom().nextBytes(it) }.also {
                    prefs.edit().putString(KEY_B64, Base64.encodeToString(it, Base64.NO_WRAP)).apply()
                }
            }
            return DeviceIdentity(key).also { cached = it }
        }
    }
}
