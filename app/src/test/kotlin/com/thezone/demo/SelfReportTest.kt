package com.thezone.demo

import org.junit.Assert.assertEquals
import org.junit.Test

class SelfReportTest {

    @Test
    fun `headcount is clamped to the casualty nibble range`() {
        SelfReport.headcount = 3
        assertEquals(3, SelfReport.headcount)
        SelfReport.headcount = 99
        assertEquals(15, SelfReport.headcount) // 15 = "15 or more"
        SelfReport.headcount = -4
        assertEquals(0, SelfReport.headcount)
        SelfReport.headcount = 0
    }
}
