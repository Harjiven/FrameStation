// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Harjiven Dodd

package com.xrworkspace.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [formatBitrate].
 *
 * The function formats a bitrate in kbps for user display, switching between
 * "kbps" and "Mbps" at the 1000 boundary and trimming redundant trailing zeros.
 */
class FormatUtilsTest {

    // --- Below 1000 kbps: raw kbps display ---

    @Test
    fun `0 kbps formats as 0 kbps`() {
        assertEquals("0 kbps", formatBitrate(0))
    }

    @Test
    fun `1 kbps formats as 1 kbps`() {
        assertEquals("1 kbps", formatBitrate(1))
    }

    @Test
    fun `500 kbps formats as 500 kbps`() {
        assertEquals("500 kbps", formatBitrate(500))
    }

    @Test
    fun `999 kbps formats as 999 kbps`() {
        assertEquals("999 kbps", formatBitrate(999))
    }

    // --- Boundary: exactly 1000 ---

    @Test
    fun `1000 kbps formats as 1 Mbps`() {
        assertEquals("1 Mbps", formatBitrate(1000))
    }

    // --- Whole-number Mbps (no decimal needed) ---

    @Test
    fun `5000 kbps formats as 5 Mbps`() {
        assertEquals("5 Mbps", formatBitrate(5000))
    }

    @Test
    fun `10000 kbps formats as 10 Mbps`() {
        assertEquals("10 Mbps", formatBitrate(10000))
    }

    @Test
    fun `20000 kbps formats as 20 Mbps`() {
        assertEquals("20 Mbps", formatBitrate(20000))
    }

    @Test
    fun `50000 kbps formats as 50 Mbps`() {
        assertEquals("50 Mbps", formatBitrate(50000))
    }

    @Test
    fun `100000 kbps formats as 100 Mbps`() {
        assertEquals("100 Mbps", formatBitrate(100000))
    }

    @Test
    fun `150000 kbps formats as 150 Mbps`() {
        assertEquals("150 Mbps", formatBitrate(150000))
    }

    // --- Fractional Mbps (one decimal place, trailing zeros trimmed) ---

    @Test
    fun `1500 kbps formats as 1_5 Mbps`() {
        assertEquals("1.5 Mbps", formatBitrate(1500))
    }

    @Test
    fun `2500 kbps formats as 2_5 Mbps`() {
        assertEquals("2.5 Mbps", formatBitrate(2500))
    }

    @Test
    fun `25500 kbps formats as 25_5 Mbps`() {
        assertEquals("25.5 Mbps", formatBitrate(25500))
    }

    @Test
    fun `3700 kbps formats as 3_7 Mbps`() {
        assertEquals("3.7 Mbps", formatBitrate(3700))
    }

    // --- Rounding: the function uses %.1f, so values are rounded to 1 decimal ---

    @Test
    fun `25050 kbps rounds to 25_1 Mbps`() {
        // 25050 / 1000.0 = 25.05 → "%.1f" = "25.1" (midpoint rounds up)
        assertEquals("25.1 Mbps", formatBitrate(25050))
    }

    @Test
    fun `25049 kbps rounds to 25 Mbps`() {
        // 25049 / 1000.0 = 25.049 → "%.1f" = "25.0" → trimEnd('0') → "25" → "25 Mbps"
        assertEquals("25 Mbps", formatBitrate(25049))
    }

    @Test
    fun `1234 kbps rounds to 1_2 Mbps`() {
        // 1234 / 1000.0 = 1.234 → "%.1f" = "1.2"
        assertEquals("1.2 Mbps", formatBitrate(1234))
    }

    @Test
    fun `9999 kbps rounds to 10 Mbps`() {
        // 9999 / 1000.0 = 9.999 → "%.1f" = "10.0" → trimEnd('0') → "10" → trimEnd('.') → "10"
        assertEquals("10 Mbps", formatBitrate(9999))
    }

    // --- Common streaming bitrates used in FrameStation presets ---

    @Test
    fun `common preset 15000 kbps`() {
        assertEquals("15 Mbps", formatBitrate(15000))
    }

    @Test
    fun `common preset 30000 kbps`() {
        assertEquals("30 Mbps", formatBitrate(30000))
    }

    @Test
    fun `common preset 60000 kbps`() {
        assertEquals("60 Mbps", formatBitrate(60000))
    }

    @Test
    fun `common preset 80000 kbps`() {
        assertEquals("80 Mbps", formatBitrate(80000))
    }

    // --- Edge cases ---

    @Test
    fun `negative value formats as negative kbps`() {
        // Shouldn't happen in practice, but verify it doesn't crash
        assertEquals("-1 kbps", formatBitrate(-1))
    }

    @Test
    fun `negative value below -1000 still hits kbps branch`() {
        // -1500 < 1000, so it takes the `else "$kbps kbps"` branch
        assertEquals("-1500 kbps", formatBitrate(-1500))
    }

    @Test
    fun `Int MAX formats without overflow or crash`() {
        // 2147483647 / 1000.0 = 2147483.647 → "%.1f" = "2147483.6" → "2147483.6 Mbps"
        val result = formatBitrate(Int.MAX_VALUE)
        assertTrue(
            "Expected Mbps format for Int.MAX_VALUE, got: $result",
            result.endsWith("Mbps"),
        )
    }

    // Helper for the assertion above
    private fun assertTrue(message: String, condition: Boolean) {
        org.junit.Assert.assertTrue(message, condition)
    }
}
