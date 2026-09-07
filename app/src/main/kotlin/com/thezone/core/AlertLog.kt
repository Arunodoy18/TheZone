package com.thezone.core

/**
 * Active emergency alerts (ALERT packets, PACKET_SPEC type 2). Carried and merged
 * like everything else — set-union over content-id — so an alert issued by one
 * authority phone floods every phone that hears any relay of it.
 *
 * Pure Kotlin. Zero Android imports. Unit-tested on the JVM.
 */
class AlertLog {

    private val lock = Any()
    private val byId = LinkedHashMap<String, AlertRecord>()

    /** @return true if this is a newly seen alert. */
    fun add(rec: AlertRecord): Boolean = synchronized(lock) {
        if (byId.containsKey(rec.contentIdHex)) return false
        byId[rec.contentIdHex] = rec
        true
    }

    fun restore(recs: Collection<AlertRecord>) = synchronized(lock) {
        for (r in recs) byId[r.contentIdHex] = r
    }

    fun isKnown(contentIdHex: String): Boolean = synchronized(lock) { byId.containsKey(contentIdHex) }

    fun all(): List<AlertRecord> = synchronized(lock) { byId.values.toList() }

    /** Not-yet-expired alerts, most severe first. */
    fun active(now: Long): List<AlertRecord> = synchronized(lock) {
        byId.values.filter { it.expiresAtMillis > now }
            .sortedWith(compareByDescending<AlertRecord> { it.category }.thenByDescending { it.issuedAtMillis })
    }

    val size: Int get() = synchronized(lock) { byId.size }

    fun clear() = synchronized(lock) { byId.clear() }
}

data class AlertRecord(
    val contentIdHex: String,
    /** 0..4 = INFO..EXTREME (PacketCodec.ALERT_*). */
    val category: Int,
    val phraseCode: Int,
    val cell: GridCell?,
    val radiusMeters: Int,
    val issuedAtMillis: Long,
    val expiresAtMillis: Long,
    val issuerHex: String,
    val hopCount: Int,
)
