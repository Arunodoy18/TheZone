package com.thezone.core

import com.thezone.identity.EcdsaSignature
import com.thezone.identity.TrustRoster
import com.thezone.packet.PacketCodec

/**
 * Collects PacketCodec.TYPE_SIG fragments as they arrive over the mesh (in any
 * order, from any relay path) and verifies the reconstructed ECDSA signature
 * against [TrustRoster] once all four are in hand.
 *
 * Pure Kotlin, zero Android imports — unit-tested on the JVM, per CLAUDE.md
 * `core/`. Fragments are buffered by [FragmentKey]; a signature is only
 * checkable once the RESOLVE/ALERT it targets has itself also been seen
 * (fragments can arrive before or after their target over a mesh with no
 * guaranteed ordering), so [sweep] is a separate, retriable step rather than
 * something [ingest] can resolve on the spot.
 */
class SignatureLog(private val maxPending: Int = 64, private val maxSettled: Int = 256) {

    data class FragmentKey(val signerHex: String, val targetType: Int, val targetPrefixHex: String)

    private val lock = Any()
    private val pending = LinkedHashMap<FragmentKey, Array<ByteArray?>>()

    // targetPrefixHex -> the signer that verified it, once settled true/false.
    private val verified = LinkedHashMap<String, String>()
    private val settled = LinkedHashSet<String>() // targetPrefixHex resolved (verified or rejected) — never reprocessed

    /** Buffer one fragment. Cheap; does no crypto — see [sweep]. */
    fun ingest(fragmentBytes: ByteArray) {
        val f = PacketCodec.decodeSigFragment(fragmentBytes) ?: return
        val key = FragmentKey(f.signerDeviceIdHex, f.targetType, f.targetContentIdPrefixHex)
        synchronized(lock) {
            if (key.targetPrefixHex in settled) return
            val slots = pending.getOrPut(key) { arrayOfNulls(PacketCodec.SIG_FRAGMENT_COUNT) }
            slots[f.fragmentIndex] = f.chunk
            evictPendingIfOverCapacity()
        }
    }

    /**
     * Attempt to verify every fragment set that is now complete. [lookupTarget]
     * resolves a (target content-id prefix, target type) back to the full
     * target packet bytes — null if that packet hasn't been seen yet, in which
     * case this fragment set is retried on a later sweep, not given up on.
     */
    fun sweep(
        roster: TrustRoster = TrustRoster.PILOT,
        lookupTarget: (targetPrefixHex: String, targetType: Int) -> ByteArray?,
    ) {
        val ready = synchronized(lock) {
            pending.entries
                .filter { (k, slots) -> k.targetPrefixHex !in settled && slots.all { it != null } }
                .map { (k, slots) -> k to reassemble(slots) }
        }
        for ((key, signature) in ready) {
            val targetBytes = lookupTarget(key.targetPrefixHex, key.targetType) ?: continue
            val pubKey = roster.publicKeyFor(key.signerHex)
            val ok = pubKey != null &&
                runCatching { EcdsaSignature.verify(pubKey, PacketCodec.contentId(targetBytes), signature) }
                    .getOrDefault(false)
            synchronized(lock) {
                pending.remove(key)
                settled.add(key.targetPrefixHex)
                if (ok) verified[key.targetPrefixHex] = key.signerHex
                evictSettledIfOverCapacity()
            }
        }
    }

    /** True once a signature for a RESOLVE/ALERT whose full content-id is [targetContentIdHex] has verified. */
    fun isVerified(targetContentIdHex: String): Boolean = synchronized(lock) {
        verified.keys.any { targetContentIdHex.startsWith(it) }
    }

    fun verifiedSigner(targetContentIdHex: String): String? = synchronized(lock) {
        verified.entries.firstOrNull { targetContentIdHex.startsWith(it.key) }?.value
    }

    val pendingCount: Int get() = synchronized(lock) { pending.size }
    val verifiedCount: Int get() = synchronized(lock) { verified.size }

    private fun reassemble(slots: Array<ByteArray?>): ByteArray {
        val out = ByteArray(PacketCodec.SIG_FRAGMENT_BYTES * PacketCodec.SIG_FRAGMENT_COUNT)
        slots.forEachIndexed { i, chunk -> chunk!!.copyInto(out, i * PacketCodec.SIG_FRAGMENT_BYTES) }
        return out
    }

    private fun evictPendingIfOverCapacity() {
        while (pending.size > maxPending) {
            val oldest = pending.keys.firstOrNull() ?: break
            pending.remove(oldest)
        }
    }

    private fun evictSettledIfOverCapacity() {
        while (settled.size > maxSettled) {
            val oldest = settled.firstOrNull() ?: break
            settled.remove(oldest)
            verified.remove(oldest)
        }
    }
}
