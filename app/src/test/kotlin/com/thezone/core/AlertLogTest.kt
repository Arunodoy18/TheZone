package com.thezone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertLogTest {

    private fun rec(id: String, cat: Int, issued: Long, expires: Long) = AlertRecord(
        contentIdHex = id, category = cat, phraseCode = 1, cell = null, radiusMeters = 500,
        issuedAtMillis = issued, expiresAtMillis = expires, issuerHex = "aa", hopCount = 0,
    )

    @Test
    fun `add dedups, active drops expired and sorts by severity`() {
        val log = AlertLog()
        val now = 1_000_000L
        assertTrue(log.add(rec("a", cat = 3, issued = now - 60_000, expires = now + 60_000)))
        assertFalse(log.add(rec("a", cat = 3, issued = now, expires = now + 999_999))) // same id
        log.add(rec("b", cat = 4, issued = now - 30_000, expires = now + 30_000))
        log.add(rec("c", cat = 2, issued = now - 10_000, expires = now - 1))           // expired

        val active = log.active(now)
        assertEquals(listOf("b", "a"), active.map { it.contentIdHex }) // EXTREME(4) before WARNING(3)
        assertEquals(3, log.size)                                       // c still stored, just not active
    }

    @Test
    fun `restore merges without re-triggering`() {
        val log = AlertLog()
        log.restore(listOf(rec("x", 4, 0, Long.MAX_VALUE)))
        assertTrue(log.isKnown("x"))
        assertFalse(log.add(rec("x", 4, 0, Long.MAX_VALUE)))
    }
}
