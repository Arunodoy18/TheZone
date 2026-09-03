package com.thezone.core

/**
 * The set of reports a responder has marked "reached / handled" (a RESOLVE
 * packet, PACKET_SPEC type 1). Carried and merged like everything else — it's
 * set-union over content-id prefixes — so the picture converges on what still
 * needs help.
 *
 * A prefix is the first N bytes of a report's content-id, hex-encoded. A report
 * is resolved when its content-id starts with any stored prefix.
 *
 * Pure Kotlin. Zero Android imports. Unit-tested on the JVM.
 */
class ResolveLog {

    private val lock = Any()
    private val prefixes = LinkedHashSet<String>()

    fun add(prefixHex: String) = synchronized(lock) { prefixes.add(prefixHex.lowercase()) }

    fun addAll(hexes: Collection<String>) = synchronized(lock) {
        hexes.forEach { prefixes.add(it.lowercase()) }
    }

    fun all(): Set<String> = synchronized(lock) { prefixes.toSet() }

    val size: Int get() = synchronized(lock) { prefixes.size }

    fun isResolved(contentIdHex: String): Boolean = synchronized(lock) {
        val id = contentIdHex.lowercase()
        prefixes.any { id.startsWith(it) }
    }

    fun clear() = synchronized(lock) { prefixes.clear() }
}
