package com.thezone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sustained-immobility detector behind the inferred TRAPPED_DEBRIS status. */
class StillnessTrackerTest {

    @Test
    fun `not still until the quiet window elapses`() {
        val t = StillnessTracker(moveThresholdMps2 = 0.8, stillAfterMillis = 300_000)
        var now = 0L
        t.onSample(0.05, now) // seed
        // a quiet minute
        repeat(12) { now += 5_000; t.onSample(0.03, now) }
        assertFalse(t.isStill(now)) // only 60 s
        // out to 5 min + 1 sample
        while (now < 301_000) { now += 5_000; t.onSample(0.04, now) }
        assertTrue(t.isStill(now))
    }

    @Test
    fun `any real motion resets the clock`() {
        val t = StillnessTracker(moveThresholdMps2 = 0.8, stillAfterMillis = 300_000)
        var now = 0L
        t.onSample(0.0, now)
        while (now < 400_000) { now += 5_000; t.onSample(0.05, now) }
        assertTrue(t.isStill(now))

        now += 5_000
        t.onSample(3.2, now) // picked up / carried
        assertFalse(t.isStill(now))
        assertEquals(now, t.stillSinceMillis())

        // still again only after another full window
        while (now < t.stillSinceMillis()!! + 300_000) { now += 5_000; t.onSample(0.05, now) }
        assertTrue(t.isStill(now))
    }

    @Test
    fun `reports nothing before the first sample`() {
        val t = StillnessTracker()
        assertNull(t.stillSinceMillis())
        assertFalse(t.isStill(999_999))
    }
}
