package com.thezone.cap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CapAlertTest {

    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
          <identifier>NDMA-2026-00123</identifier>
          <sender>ndma@nic.in</sender>
          <sent>2026-09-09T10:00:00+05:30</sent>
          <status>Actual</status>
          <msgType>Alert</msgType>
          <scope>Public</scope>
          <info>
            <category>Geo</category>
            <event>Earthquake</event>
            <urgency>Immediate</urgency>
            <severity>Extreme</severity>
            <certainty>Observed</certainty>
            <headline>Evacuate low-lying areas</headline>
            <description>Aftershocks likely across the district.</description>
            <instruction>Evacuate immediately and move to higher ground.</instruction>
            <expires>2026-09-09T14:00:00+05:30</expires>
            <area>
              <areaDesc>Rasuwa District</areaDesc>
              <circle>28.2814,85.3779 5.0</circle>
            </area>
          </info>
        </alert>
    """.trimIndent()

    @Test
    fun `parses every field from a well-formed CAP 1_2 document`() {
        val f = CapAlert.parse(sample)!!
        assertEquals("NDMA-2026-00123", f.identifier)
        assertEquals("Earthquake", f.event)
        assertEquals("Evacuate low-lying areas", f.headline)
        assertEquals("Evacuate immediately and move to higher ground.", f.instruction)
        assertEquals("Extreme", f.severity)
        assertEquals("Rasuwa District", f.areaDesc)
        assertEquals(28.2814, f.centerLat!!, 1e-6)
        assertEquals(85.3779, f.centerLon!!, 1e-6)
        assertEquals(5.0, f.radiusKm!!, 1e-6)
        assertEquals(240, f.expiresMinutesFromSent)   // 4 hours
    }

    @Test
    fun `malformed XML returns null, never throws`() {
        assertNull(CapAlert.parse("not xml at all <<<"))
        assertNull(CapAlert.parse(""))
    }

    @Test
    fun `missing optional fields degrade gracefully`() {
        val minimal = """
            <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
              <info><event>Flood</event></info>
            </alert>
        """.trimIndent()
        val f = CapAlert.parse(minimal)!!
        assertEquals("cap-unknown", f.identifier)
        assertEquals("Flood", f.event)
        assertEquals("Flood", f.headline)          // falls back to event
        assertEquals("Unknown", f.severity)
        assertNull(f.centerLat)
        assertNull(f.radiusKm)
        assertNull(f.expiresMinutesFromSent)
    }

    @Test
    fun `severity maps onto the ALERT category ladder`() {
        assertEquals(4, CapAlert.categoryFor("Extreme"))
        assertEquals(3, CapAlert.categoryFor("Severe"))
        assertEquals(2, CapAlert.categoryFor("Moderate"))
        assertEquals(1, CapAlert.categoryFor("Minor"))
        assertEquals(0, CapAlert.categoryFor("Unknown"))
        assertEquals(0, CapAlert.categoryFor("garbage"))
    }

    @Test
    fun `phrase matching prefers the most specific keyword hit`() {
        val f = CapAlert.parse(sample)!!
        // "Evacuate ... move to higher ground" — evacuate appears first in the
        // rule table and should win over the later "higher ground" rule.
        assertEquals(1, CapAlert.phraseCodeFor(f))
    }

    @Test
    fun `unmatched text falls back to the generic ALERT phrase`() {
        val f = CapFields(
            identifier = "x", sent = null, event = "Something", headline = "Something unusual",
            instruction = "", severity = "Unknown", urgency = "Unknown", certainty = "Unknown",
            areaDesc = null, centerLat = null, centerLon = null, radiusKm = null,
            expiresMinutesFromSent = null,
        )
        assertEquals(0, CapAlert.phraseCodeFor(f))
    }

    @Test
    fun `XXE-style external entity is not resolved`() {
        val hostile = """
            <?xml version="1.0"?>
            <!DOCTYPE alert [<!ENTITY xxe SYSTEM "file:///etc/hostname">]>
            <alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
              <info><event>&xxe;</event></info>
            </alert>
        """.trimIndent()
        // disallow-doctype-decl rejects the whole document rather than
        // resolving the entity — parse() must fail closed, not leak a file.
        assertNull(CapAlert.parse(hostile))
    }

    @Test
    fun `circle radius parses independently of point precision`() {
        assertTrue(CapAlert.parse(sample)!!.radiusKm == 5.0)
    }
}
