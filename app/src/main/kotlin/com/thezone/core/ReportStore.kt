package com.thezone.core

import com.thezone.packet.Packet
import com.thezone.packet.PacketCodec

/**
 * The store-carry-forward set (CLAUDE.md `core/`, PACKET_SPEC "Relay rules").
 *
 * Content-addressed: reports are keyed by [PacketCodec.contentId], which excludes
 * the hop nibble and `auth`, so the same message heard at any hop count is one
 * entry. Merge is set-union over those keys — a CRDT by construction, no library.
 *
 * Pure Kotlin. Zero Android imports. Unit-tested on the JVM.
 */
class ReportStore(
    /** Hard cap; the least-recently-heard non-own report is evicted past this. */
    private val maxReports: Int = 2_000,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    private val lock = Any()
    private val byId = LinkedHashMap<String, StoredReport>()
    private var relayCursor = 0

    /** Hex device_id of this device, so its own heartbeat is never relayed by it. */
    @Volatile
    var ownDeviceIdHex: String? = null

    val size: Int get() = synchronized(lock) { byId.size }

    /**
     * Ingest one received advertisement.
     *
     * Verifies the auth *shape* (length + non-zero — no key needed, PACKET_SPEC
     * relay rule 1), decodes, then either stores a new report or folds the
     * reception into the existing one (keeping the lowest hop count seen — the
     * shortest path — and the strongest RSSI).
     */
    fun accept(
        bytes: ByteArray,
        rssiDbm: Int,
        receivedAtMillis: Long = nowMillis(),
    ): AcceptOutcome {
        if (bytes.size != Packet.SIZE_BYTES) return AcceptOutcome.REJECTED_MALFORMED
        if (!PacketCodec.authShapeValid(bytes)) return AcceptOutcome.REJECTED_AUTH_SHAPE

        val packet = runCatching { PacketCodec.decode(bytes) }.getOrNull()
            ?: return AcceptOutcome.REJECTED_MALFORMED

        val id = PacketCodec.contentId(bytes).toHexLower()
        val isOwn = ownDeviceIdHex != null && packet.deviceId.toHexLower() == ownDeviceIdHex

        synchronized(lock) {
            val existing = byId[id]
            if (existing == null) {
                byId[id] = StoredReport(
                    contentId = id,
                    bytes = bytes.copyOf(),
                    packet = packet,
                    firstHeardAtMillis = receivedAtMillis,
                    lastHeardAtMillis = receivedAtMillis,
                    bestRssiDbm = rssiDbm,
                    lastRssiDbm = rssiDbm,
                    receivedHopCount = packet.hopCount,
                    hopsSeen = setOf(packet.hopCount),
                    timesHeard = 1,
                    isOwn = isOwn,
                )
                evictIfOverCapacity()
                return AcceptOutcome.NEW
            }

            var changed = false
            var bytesForEntry = existing.bytes
            var packetForEntry = existing.packet
            var hopForEntry = existing.receivedHopCount
            if (packet.hopCount < existing.receivedHopCount) {
                bytesForEntry = bytes.copyOf() // a shorter path — carry this copy forward
                packetForEntry = packet
                hopForEntry = packet.hopCount
                changed = true
            }
            byId[id] = existing.copy(
                bytes = bytesForEntry,
                packet = packetForEntry,
                receivedHopCount = hopForEntry,
                hopsSeen = existing.hopsSeen + packet.hopCount,
                lastHeardAtMillis = receivedAtMillis,
                bestRssiDbm = maxOf(existing.bestRssiDbm, rssiDbm),
                lastRssiDbm = rssiDbm,
                timesHeard = existing.timesHeard + 1,
                isOwn = existing.isOwn || isOwn,
            )
            return if (changed) AcceptOutcome.UPDATED else AcceptOutcome.DUPLICATE
        }
    }

    /**
     * Set-union merge of raw 31-byte records from another store / a synced file.
     * @return how many were newly added.
     */
    fun mergeFrom(raw: Collection<ByteArray>): Int {
        var added = 0
        for (r in raw) if (accept(r, rssiDbm = 0) == AcceptOutcome.NEW) added++
        return added
    }

    /** Caller holds [lock]. Trims to [maxReports], never evicting this device's own reports. */
    private fun evictIfOverCapacity() {
        if (byId.size <= maxReports) return
        val victims = byId.values
            .filter { !it.isOwn }
            .sortedBy { it.lastHeardAtMillis }
            .take(byId.size - maxReports)
        victims.forEach { byId.remove(it.contentId) }
    }

    fun get(contentIdHex: String): StoredReport? = synchronized(lock) { byId[contentIdHex] }

    fun all(): List<StoredReport> = synchronized(lock) { byId.values.toList() }

    /** Every stored record as raw bytes — for JSON export / sync. */
    fun snapshotRaw(): List<ByteArray> = synchronized(lock) { byId.values.map { it.bytes.copyOf() } }

    fun clear() = synchronized(lock) {
        byId.clear()
        relayCursor = 0
    }

    /**
     * Up to [max] carried packets to rebroadcast, hop incremented (PACKET_SPEC
     * rule 3: the *copy* is incremented, the stored original is untouched).
     *
     * Round-robin via a rotating cursor so, over successive calls, every carried
     * report gets air time and relaying never starves this device's own signal
     * (rule 5). Excludes this device's own reports and anything already at the
     * hop ceiling (rule 4).
     */
    fun relayBatch(
        max: Int,
        /** Optional gate by sender device_id hex — e.g. skip devices believed silent. */
        includeDevice: (String) -> Boolean = { true },
    ): List<ByteArray> {
        if (max <= 0) return emptyList()
        synchronized(lock) {
            val candidates = byId.values
                .filter {
                    !it.isOwn &&
                        it.receivedHopCount < Packet.MAX_HOPS &&
                        includeDevice(it.packet.deviceId.toHexLower())
                }
                .sortedBy { it.contentId } // deterministic order for a stable cursor
            if (candidates.isEmpty()) return emptyList()

            val start = relayCursor % candidates.size
            val take = minOf(max, candidates.size)
            val out = ArrayList<ByteArray>(take)
            for (i in 0 until take) {
                out.add(PacketCodec.incrementHop(candidates[(start + i) % candidates.size].bytes))
            }
            relayCursor = (start + take) % candidates.size
            return out
        }
    }
}

enum class AcceptOutcome {
    /** First time this message identity was seen — stored. */
    NEW,

    /** Already held; reception folded in (timesHeard / lastHeard / RSSI). */
    DUPLICATE,

    /** Already held, and this copy had a lower hop count, so it replaced the stored bytes. */
    UPDATED,

    /** Not 31 bytes, or decode failed. */
    REJECTED_MALFORMED,

    /** auth field is missing or all-zero (relay rule 1). */
    REJECTED_AUTH_SHAPE,
}

/** One message identity held in the store. [bytes] is the record as received — never mutated. */
data class StoredReport(
    val contentId: String,
    val bytes: ByteArray,
    val packet: Packet,
    val firstHeardAtMillis: Long,
    val lastHeardAtMillis: Long,
    /** Strongest RSSI ever seen from this record — "closest approach". */
    val bestRssiDbm: Int,
    /** RSSI of the most recent reception — for the live Dig Here bar. */
    val lastRssiDbm: Int,
    /** hop_count as it arrived on the wire (lowest kept). */
    val receivedHopCount: Int,
    /** Every distinct hop count this identity has arrived at — path-diversity signal. */
    val hopsSeen: Set<Int> = emptySet(),
    val timesHeard: Int,
    val isOwn: Boolean,
) {
    /**
     * Hops from the origin as this device would present / relay it: own signal is
     * 0, a carried packet is what this device would rebroadcast with, i.e. one
     * more than it arrived with. In the demo this is the "hop count 2" phone C
     * shows for a packet that reached it via phone B.
     */
    val hopsFromOrigin: Int get() = if (isOwn) 0 else receivedHopCount + 1

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StoredReport) return false
        return contentId == other.contentId &&
            bytes.contentEquals(other.bytes) &&
            firstHeardAtMillis == other.firstHeardAtMillis &&
            lastHeardAtMillis == other.lastHeardAtMillis &&
            bestRssiDbm == other.bestRssiDbm &&
            receivedHopCount == other.receivedHopCount &&
            timesHeard == other.timesHeard &&
            isOwn == other.isOwn
    }

    override fun hashCode(): Int {
        var result = contentId.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + lastHeardAtMillis.hashCode()
        result = 31 * result + receivedHopCount
        result = 31 * result + timesHeard
        return result
    }
}

private fun ByteArray.toHexLower(): String = joinToString("") { "%02x".format(it) }
