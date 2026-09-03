package com.thezone.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolveLogTest {

    @Test
    fun `a report is resolved when its content-id starts with a stored prefix`() {
        val log = ResolveLog()
        log.add("aabbccddeeff00") // 7-byte prefix, hex
        assertTrue(log.isResolved("aabbccddeeff0012345678"))
        assertTrue(log.isResolved("AABBCCDDEEFF0099")) // case-insensitive
        assertFalse(log.isResolved("aabbccddeeff01"))
        assertFalse(log.isResolved("00aabbccddeeff00"))
    }

    @Test
    fun `add is idempotent and addAll merges`() {
        val log = ResolveLog()
        assertTrue(log.add("aa"))
        assertFalse(log.add("aa"))
        log.addAll(listOf("bb", "cc", "aa"))
        assertEquals(3, log.size)
        assertEquals(setOf("aa", "bb", "cc"), log.all())
    }
}
