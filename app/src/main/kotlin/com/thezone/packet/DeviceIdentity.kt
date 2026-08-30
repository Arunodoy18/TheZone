package com.thezone.packet

import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The whole identity story for the demo (docs/PRD.md §2 — real crypto is
 * explicitly cut): a per-install random 32-byte key.
 *
 *   device_id = first 6 bytes of SHA-256(key)
 *   auth      = first 4 bytes of SHA-256(key ‖ payload[0..18])
 *
 * Not real crypto. It gives the same anti-spoofing story at demo scale for an
 * hour less work. `java.security` is JDK, not Android — this stays JVM-testable.
 */
class DeviceIdentity(val key: ByteArray) {

    init {
        require(key.size == KEY_BYTES) { "key must be $KEY_BYTES bytes, got ${key.size}" }
    }

    /** First 6 bytes of SHA-256(key). Stable for the life of the install. */
    val deviceId: ByteArray by lazy { sha256(key).copyOf(Packet.DEVICE_ID_BYTES) }

    companion object {
        const val KEY_BYTES = 32

        fun random(random: SecureRandom = SecureRandom()): DeviceIdentity =
            DeviceIdentity(ByteArray(KEY_BYTES).also(random::nextBytes))

        /** SHA-256 over the concatenation of [parts]. */
        fun sha256(vararg parts: ByteArray): ByteArray {
            val md = MessageDigest.getInstance("SHA-256")
            for (part in parts) md.update(part)
            return md.digest()
        }
    }
}
