package com.thezone.cap

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.time.Duration
import java.time.OffsetDateTime
import javax.xml.parsers.DocumentBuilderFactory

/**
 * CAP — the Common Alerting Protocol (OASIS) — is the open XML format real
 * emergency systems speak underneath: NDMA's SACHET, the US's IPAWS, the
 * feeds that ultimately trigger a Cell Broadcast. It is a message FORMAT,
 * not a delivery mechanism, so parsing one doesn't touch the network (Zone
 * never fetches a CAP feed itself — CLAUDE.md rule 4) — this reads a CAP XML
 * file the user already has (downloaded, emailed, shared) from before the
 * tower died, so Zone can carry the government's own last warning forward
 * into the mesh after the government's own channel goes dark.
 *
 * Pure Kotlin / JDK-only (javax.xml.parsers, part of the standard library on
 * both the JVM and Android — no dependency added), unit-testable exactly
 * like [com.thezone.packet.PacketCodec].
 */
data class CapFields(
    val identifier: String,
    val sent: String?,
    val event: String,
    val headline: String,
    val instruction: String,
    val severity: String,
    val urgency: String,
    val certainty: String,
    val areaDesc: String?,
    val centerLat: Double?,
    val centerLon: Double?,
    val radiusKm: Double?,
    val expiresMinutesFromSent: Int?,
)

object CapAlert {

    /** Parses a CAP 1.2 `<alert>` document. Null on anything malformed — never throws. */
    fun parse(xml: String): CapFields? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // No DTDs: a CAP file could otherwise be an XXE vector, and a
            // disaster alert has no legitimate reason to reference an
            // external entity. Fails the whole parse if unsupported, not
            // silently — see the outer runCatching.
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        doc.documentElement.normalize()

        val root = doc.documentElement
        val info = firstDescendant(root, "info") ?: root
        val area = firstDescendant(info, "area")
        val (lat, lon, radiusKm) = parseCircle(text(area, "circle"))

        CapFields(
            identifier = text(root, "identifier") ?: "cap-unknown",
            sent = text(root, "sent"),
            event = text(info, "event") ?: "",
            headline = text(info, "headline") ?: text(info, "event") ?: "Alert",
            instruction = text(info, "instruction") ?: text(info, "description") ?: "",
            severity = text(info, "severity") ?: "Unknown",
            urgency = text(info, "urgency") ?: "Unknown",
            certainty = text(info, "certainty") ?: "Unknown",
            areaDesc = text(area, "areaDesc"),
            centerLat = lat,
            centerLon = lon,
            radiusKm = radiusKm,
            expiresMinutesFromSent = minutesBetween(text(root, "sent"), text(info, "expires")),
        )
    }.getOrNull()

    /** docs/PACKET_SPEC.md ALERT category (0 INFO .. 4 EXTREME) from CAP severity. */
    fun categoryFor(severity: String): Int = when (severity.lowercase()) {
        "extreme" -> 4
        "severe" -> 3
        "moderate" -> 2
        "minor" -> 1
        else -> 0
    }

    /**
     * Best-effort match onto Zone's fixed 13-phrase table ([com.thezone.packet.AlertText])
     * — the packet is 31 bytes and can't carry CAP's free text, so this is a
     * deliberate, documented compression: the closest of the pre-agreed
     * phrases, chosen from the CAP headline / instruction / event text.
     * Falls back to phrase 0, the generic "ALERT".
     */
    fun phraseCodeFor(fields: CapFields): Int {
        val hay = "${fields.headline} ${fields.instruction} ${fields.event}".lowercase()
        val rules = listOf(
            listOf("evacuat") to 1,
            listOf("high ground", "higher ground") to 2,
            listOf("shelter") to 3,
            listOf("aftershock") to 4,
            listOf("flash flood") to 5,
            listOf("landslide") to 6,
            listOf("fire") to 7,
            listOf("gas leak") to 8,
            listOf("rescue") to 9,
            listOf("aid station", "ration", "water and") to 10,
            listOf("route blocked", "road blocked", "route closed") to 11,
            listOf("all clear") to 12,
        )
        return rules.firstOrNull { (keywords, _) -> keywords.any { hay.contains(it) } }?.second ?: 0
    }

    /** First element anywhere in [el]'s subtree whose local name is [tag], namespace-agnostic. */
    private fun firstDescendant(el: Element, tag: String): Element? {
        val list = el.getElementsByTagNameNS("*", tag)
        return (0 until list.length).asSequence().mapNotNull { list.item(it) as? Element }.firstOrNull()
    }

    private fun text(el: Element?, tag: String): String? {
        val found = el?.let { firstDescendant(it, tag) } ?: return null
        return found.textContent?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** CAP `<circle>` is "lat,lon radius_km". */
    private fun parseCircle(circle: String?): Triple<Double?, Double?, Double?> {
        if (circle == null) return Triple(null, null, null)
        return runCatching {
            val (point, radius) = circle.trim().split(Regex("\\s+"), limit = 2)
            val (lat, lon) = point.split(",").map { it.trim().toDouble() }
            Triple(lat, lon, radius.trim().toDouble())
        }.getOrElse { Triple(null, null, null) }
    }

    private fun minutesBetween(sentIso: String?, otherIso: String?): Int? {
        if (sentIso == null || otherIso == null) return null
        return runCatching {
            Duration.between(OffsetDateTime.parse(sentIso), OffsetDateTime.parse(otherIso)).toMinutes().toInt()
        }.getOrNull()
    }
}
