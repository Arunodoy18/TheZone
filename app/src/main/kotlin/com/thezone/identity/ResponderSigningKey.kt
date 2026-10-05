package com.thezone.identity

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

/**
 * The per-install ECDSA P-256 keypair a RESPONDER phone signs RESOLVE/ALERT
 * packets with (docs/PACKET_SPEC.md "SIG" packet type, [EcdsaSignature]).
 * Generated once, kept in the Android Keystore — hardware-backed where the
 * device supports it — so the private key is never held as a plain byte
 * array and never leaves the secure element. `android.security.keystore` is
 * a platform API, not a third-party dependency (CLAUDE.md "no crypto library").
 *
 * This key existing on a phone does not by itself make that phone trusted —
 * it only becomes a verified signer once its public key (see
 * [publicKeyRawPoint]) is added to [TrustRoster.PILOT] and shipped in an app
 * update. See docs/RESPONDER_PROVISIONING.md.
 */
object ResponderSigningKey {

    private const val ALIAS = "zone_responder_signing_key"
    private const val PROVIDER = "AndroidKeyStore"

    /** Generates the keypair if this phone doesn't have one yet. Safe to call repeatedly. */
    fun ensureGenerated(context: Context) {
        if (hasKey(context)) return
        runCatching {
            val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, PROVIDER)
            kpg.initialize(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
                )
                    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .build(),
            )
            kpg.generateKeyPair()
        }
    }

    // context is unused below (the Android Keystore is process-global, not tied
    // to a Context) — kept on every function anyway so call sites read uniformly
    // and so a future StrongBox-availability check has somewhere to go.
    @Suppress("UNUSED_PARAMETER")
    fun hasKey(context: Context): Boolean =
        runCatching { keyStore().containsAlias(ALIAS) }.getOrDefault(false)

    /** Raw uncompressed EC point (65 bytes) — the value a [RosterEntry.publicKeyHex] needs. */
    @Suppress("UNUSED_PARAMETER")
    fun publicKeyRawPoint(context: Context): ByteArray? = runCatching {
        val cert = keyStore().getCertificate(ALIAS) ?: return null
        EcdsaSignature.encodePublicKey(cert.publicKey as ECPublicKey)
    }.getOrNull()

    /** This phone's own device_id, hex — the value a [RosterEntry.deviceIdHex] needs. */
    fun deviceIdHex(context: Context): String =
        DeviceKeyStore.identity(context).deviceId.joinToString("") { "%02x".format(it) }

    /** Sign [message] with this phone's key. Null if it has no key (not provisioned yet, or Keystore refused). */
    @Suppress("UNUSED_PARAMETER")
    fun sign(context: Context, message: ByteArray): ByteArray? = runCatching {
        val entry = keyStore().getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
        EcdsaSignature.sign(entry.privateKey, message)
    }.getOrNull()

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }
}
