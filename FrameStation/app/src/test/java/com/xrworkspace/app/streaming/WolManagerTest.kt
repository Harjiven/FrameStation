// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [WolManager] MAC address validation and normalization.
 *
 * Note: [WolManager.sendWakePacket] is not tested here because it requires
 * network I/O via [com.limelight.nvstream.wol.WakeOnLanSender]. Integration
 * tests would be needed for end-to-end WoL verification.
 */
class WolManagerTest {

    private val wolManager = WolManager()

    // --- MAC address validation: valid formats ---

    @Test
    fun `valid MAC with colons uppercase`() {
        assertTrue(wolManager.isValidMacAddress("AA:BB:CC:DD:EE:FF"))
    }

    @Test
    fun `valid MAC with colons lowercase`() {
        assertTrue(wolManager.isValidMacAddress("aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `valid MAC with colons mixed case`() {
        assertTrue(wolManager.isValidMacAddress("aA:Bb:cC:Dd:eE:fF"))
    }

    @Test
    fun `valid MAC with dashes uppercase`() {
        assertTrue(wolManager.isValidMacAddress("AA-BB-CC-DD-EE-FF"))
    }

    @Test
    fun `valid MAC with dashes lowercase`() {
        assertTrue(wolManager.isValidMacAddress("aa-bb-cc-dd-ee-ff"))
    }

    @Test
    fun `valid MAC with dashes mixed case`() {
        assertTrue(wolManager.isValidMacAddress("aA-Bb-cC-Dd-eE-fF"))
    }

    @Test
    fun `valid MAC with leading and trailing whitespace`() {
        assertTrue(wolManager.isValidMacAddress("  AA:BB:CC:DD:EE:FF  "))
    }

    @Test
    fun `valid MAC all zeros`() {
        assertTrue(wolManager.isValidMacAddress("00:00:00:00:00:00"))
    }

    @Test
    fun `valid MAC all F`() {
        assertTrue(wolManager.isValidMacAddress("FF:FF:FF:FF:FF:FF"))
    }

    @Test
    fun `valid MAC numeric only`() {
        assertTrue(wolManager.isValidMacAddress("12:34:56:78:90:12"))
    }

    // --- MAC address validation: invalid formats ---

    @Test
    fun `invalid MAC empty string`() {
        assertFalse(wolManager.isValidMacAddress(""))
    }

    @Test
    fun `invalid MAC whitespace only`() {
        assertFalse(wolManager.isValidMacAddress("   "))
    }

    @Test
    fun `invalid MAC too short`() {
        assertFalse(wolManager.isValidMacAddress("AA:BB:CC:DD:EE"))
    }

    @Test
    fun `invalid MAC too long`() {
        assertFalse(wolManager.isValidMacAddress("AA:BB:CC:DD:EE:FF:00"))
    }

    @Test
    fun `invalid MAC no separators`() {
        assertFalse(wolManager.isValidMacAddress("AABBCCDDEEFF"))
    }

    @Test
    fun `invalid MAC dot separators`() {
        assertFalse(wolManager.isValidMacAddress("AA.BB.CC.DD.EE.FF"))
    }

    @Test
    fun `invalid MAC mixed separators`() {
        assertFalse(wolManager.isValidMacAddress("AA:BB-CC:DD-EE:FF"))
    }

    @Test
    fun `invalid MAC non-hex characters`() {
        assertFalse(wolManager.isValidMacAddress("GG:HH:II:JJ:KK:LL"))
    }

    @Test
    fun `invalid MAC single digit groups`() {
        assertFalse(wolManager.isValidMacAddress("A:B:C:D:E:F"))
    }

    @Test
    fun `invalid MAC triple digit groups`() {
        assertFalse(wolManager.isValidMacAddress("AAA:BBB:CCC:DDD:EEE:FFF"))
    }

    @Test
    fun `invalid MAC random text`() {
        assertFalse(wolManager.isValidMacAddress("not a mac address"))
    }

    // --- MAC address normalization ---

    @Test
    fun `normalize replaces dashes with colons`() {
        assertEquals("AA:BB:CC:DD:EE:FF", WolManager.normalizeMacAddress("AA-BB-CC-DD-EE-FF"))
    }

    @Test
    fun `normalize converts to uppercase`() {
        assertEquals("AA:BB:CC:DD:EE:FF", WolManager.normalizeMacAddress("aa:bb:cc:dd:ee:ff"))
    }

    @Test
    fun `normalize trims whitespace`() {
        assertEquals("AA:BB:CC:DD:EE:FF", WolManager.normalizeMacAddress("  AA:BB:CC:DD:EE:FF  "))
    }

    @Test
    fun `normalize handles dashes lowercase with whitespace`() {
        assertEquals("AA:BB:CC:DD:EE:FF", WolManager.normalizeMacAddress("  aa-bb-cc-dd-ee-ff  "))
    }

    @Test
    fun `normalize preserves already normalized MAC`() {
        assertEquals("AA:BB:CC:DD:EE:FF", WolManager.normalizeMacAddress("AA:BB:CC:DD:EE:FF"))
    }
}
