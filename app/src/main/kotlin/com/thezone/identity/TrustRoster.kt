package com.thezone.identity

/** One provisioned responder: which device, and its ECDSA P-256 public key. */
data class RosterEntry(
    val deviceIdHex: String,
    val publicKeyHex: String,
    val label: String = "",
)

/**
 * The fixed, pilot-scale trust model: "who is allowed to issue a RESOLVE or
 * ALERT that the ECDSA signature machinery (docs/PACKET_SPEC.md "SIG" packet,
 * [EcdsaSignature], [com.thezone.core.SignatureLog]) will treat as verified."
 *
 * Deliberately a fixed list baked into the app, not a runtime-issued
 * credential — the simplest thing that is still real security for a
 * single-agency pilot. Provisioning is a manual, offline step: see
 * docs/RESPONDER_PROVISIONING.md. Adding or revoking a responder means
 * shipping an app update with a new [PILOT] list, exactly like updating the
 * pre-shared responder key it now sits alongside.
 *
 * While [PILOT] is empty (the out-of-box state, and every build until you
 * provision a real responder), Zone's *existing* trust gate — the pre-shared
 * key MAC checked in [com.thezone.transport.TransportController] — is what
 * decides whether a RESOLVE/ALERT is honoured, completely unchanged. This
 * roster only ever adds an additional, stronger signal
 * ([com.thezone.transport.TransportController.isSignatureVerified]) on top of
 * that; it does not replace it yet. See docs/RESPONDER_PROVISIONING.md
 * "phase 2" for when to flip that switch.
 */
class TrustRoster(private val entries: List<RosterEntry>) {

    private val byDeviceId: Map<String, RosterEntry> = entries.associateBy { it.deviceIdHex.lowercase() }

    val isProvisioned: Boolean get() = entries.isNotEmpty()

    fun contains(deviceIdHex: String): Boolean = byDeviceId.containsKey(deviceIdHex.lowercase())

    fun publicKeyFor(deviceIdHex: String): ByteArray? =
        byDeviceId[deviceIdHex.lowercase()]?.publicKeyHex?.let(::hexToBytesOrNull)

    fun labelFor(deviceIdHex: String): String? = byDeviceId[deviceIdHex.lowercase()]?.label

    companion object {
        /**
         * The live pilot roster. Empty until you provision a real responder —
         * see docs/RESPONDER_PROVISIONING.md. Example entry, once you have one:
         *
         *   RosterEntry(
         *       deviceIdHex = "a1b2c3d4e5f6",   // Debug -> H0/H2 -> "device_id"
         *       publicKeyHex = "04...",          // Debug -> Responder signing key -> "Copy public key"
         *       label = "Gangtok EOC — responder 1",
         *   ),
         */
        val PILOT: TrustRoster = TrustRoster(emptyList())
    }
}

private fun hexToBytesOrNull(hex: String): ByteArray? {
    val clean = hex.trim()
    if (clean.length % 2 != 0) return null
    return runCatching {
        ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
    }.getOrNull()
}
