package com.thezone.identity

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustRosterTest {

    @Test
    fun `empty roster is not provisioned and knows nobody`() {
        val roster = TrustRoster(emptyList())
        assertFalse(roster.isProvisioned)
        assertFalse(roster.contains("aabbccddeeff"))
        assertNull(roster.publicKeyFor("aabbccddeeff"))
    }

    @Test
    fun `PILOT ships empty until a responder is provisioned`() {
        // Documents the deliberate default (docs/RESPONDER_PROVISIONING.md) —
        // this test starts failing the day someone provisions the real roster,
        // which is exactly the signal to update it, not a bug.
        assertFalse(TrustRoster.PILOT.isProvisioned)
    }

    @Test
    fun `looks up a provisioned entry, case-insensitively`() {
        val pubKeyHex = "04" + "11".repeat(64)
        val roster = TrustRoster(
            listOf(RosterEntry(deviceIdHex = "A1B2C3D4E5F6", publicKeyHex = pubKeyHex, label = "test")),
        )
        assertTrue(roster.isProvisioned)
        assertTrue(roster.contains("a1b2c3d4e5f6"))
        assertTrue(roster.contains("A1B2C3D4E5F6"))
        assertEquals("test", roster.labelFor("a1b2c3d4e5f6"))
        val expected = ByteArray(65) { if (it == 0) 0x04 else 0x11 }
        assertArrayEquals(expected, roster.publicKeyFor("a1b2c3d4e5f6"))
    }

    @Test
    fun `unknown device id is not in the roster`() {
        val roster = TrustRoster(listOf(RosterEntry("aabbccddeeff", "04" + "00".repeat(64))))
        assertFalse(roster.contains("112233445566"))
        assertNull(roster.publicKeyFor("112233445566"))
    }

    @Test
    fun `malformed public key hex resolves to null rather than throwing`() {
        val roster = TrustRoster(listOf(RosterEntry("aabbccddeeff", "not-hex-and-odd-length")))
        assertNull(roster.publicKeyFor("aabbccddeeff"))
    }
}
