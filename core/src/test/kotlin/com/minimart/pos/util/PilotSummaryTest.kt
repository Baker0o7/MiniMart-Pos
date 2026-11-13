package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PilotSummaryTest {
    private val checks = listOf(
        HealthCheck("backup", "Backup", HealthLevel.BAD, "Last backup was 20 days ago."),
        HealthCheck("data", "Sales and stock records", HealthLevel.GOOD, "Everything adds up."),
        HealthCheck("sync", "Till sync", HealthLevel.WARN, "Last synced 9 hours ago."))

    @Test fun `summary has the day, the verdict and one tagged line per check`() {
        val t = PilotSummary.format("Table & Tide", "Fri 13 Nov 2026", "1.33.0", "1 problem needs attention", checks, 42, "KES 12,345.50", 2)
        val lines = t.lines()
        assertEquals("MiniMart POS pilot summary", lines[0])
        assertTrue("Shop: Table & Tide" in lines)
        assertTrue("Today: 42 sale(s), KES 12,345.50" in lines)
        assertTrue("Crash reports this week: 2" in lines)
        assertTrue("[PROBLEM] Backup: Last backup was 20 days ago." in lines)
        assertTrue("[OK] Sales and stock records: Everything adds up." in lines)
        assertTrue("[CHECK] Till sync: Last synced 9 hours ago." in lines)
    }

    @Test fun `line breaks inside text cannot fake extra lines`() {
        val t = PilotSummary.format("Shop\n[OK] Backup: fine", "d", "v", "h\r\nx", checks, 0, "KES 0", 0)
        assertFalse(t.lines().any { it.startsWith("[OK] Backup") })
    }

    @Test fun `a missing shop name and very long details are handled`() {
        val long = HealthCheck("x", "T", HealthLevel.WARN, "a".repeat(1000))
        val t = PilotSummary.format("  ", "d", "v", "h", listOf(long), 0, "KES 0", 0)
        assertTrue("Shop: (no name)" in t.lines())
        assertTrue(t.lines().last().length < 200)
    }
}
