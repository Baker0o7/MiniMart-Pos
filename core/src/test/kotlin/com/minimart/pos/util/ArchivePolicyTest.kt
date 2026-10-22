package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class ArchivePolicyTest {
    private val utc: ZoneId = ZoneOffset.UTC
    private fun ms(date: String, hour: Int = 12) = LocalDate.parse(date).atTime(hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun cutoffIsStartOfDayMonthsBack() {
        assertEquals(ms("2024-10-22", 0), ArchivePolicy.cutoff(ms("2026-10-22"), 24, utc))
    }

    @Test fun neverArchivesUnderAYear() {
        assertEquals(12, ArchivePolicy.clampMonths(1))
        assertEquals(ArchivePolicy.cutoff(ms("2026-10-22"), 12, utc), ArchivePolicy.cutoff(ms("2026-10-22"), 3, utc))
        assertEquals(120, ArchivePolicy.clampMonths(9999))
    }

    @Test fun cutoffHandlesMonthEnds() {
        // 31 March minus 12 months is 31 March; 29 Feb 2028 minus 12 months is 28 Feb 2027.
        assertEquals(ms("2025-03-31", 0), ArchivePolicy.cutoff(ms("2026-03-31"), 12, utc))
        assertEquals(ms("2027-02-28", 0), ArchivePolicy.cutoff(ms("2028-02-29"), 12, utc))
    }

    @Test fun csvQuotesAwkwardText() {
        assertEquals("plain", ArchivePolicy.csvField("plain"))
        assertEquals("\"a,b\"", ArchivePolicy.csvField("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", ArchivePolicy.csvField("say \"hi\""))
        assertEquals("\"line1\nline2\"", ArchivePolicy.csvField("line1\nline2"))
    }

    @Test fun csvDefusesSpreadsheetFormulas() {
        assertEquals("'=SUM(A1)", ArchivePolicy.csvField("=SUM(A1)"))
        assertEquals("'@cmd", ArchivePolicy.csvField("@cmd"))
        assertEquals("-12.5", ArchivePolicy.csvField("-12.5"))   // a real negative number stays a number
    }

    @Test fun headerAndRowsHaveTheSameWidth() {
        val width = ArchivePolicy.CSV_HEADER.split(",").size
        assertEquals(22, width)
        assertEquals(width, ArchivePolicy.csvRow(List(width) { "x" }).split(",").size)
    }

    @Test fun fileNameIsSortableAndSafe() {
        assertEquals("minimart-sales-archive-20261022-0945.csv.gz", ArchivePolicy.fileName(ms("2026-10-22", 9) + 45 * 60_000, utc))
    }

    @Test fun planDescription() {
        assertTrue(ArchivePolicy.describe(ArchivePlan(0, 0, 0, null, null), utc).startsWith("Nothing to archive"))
        val text = ArchivePolicy.describe(ArchivePlan(0, 5, 12, ms("2024-01-05"), ms("2024-09-30")), utc)
        assertTrue(text.contains("5 sale(s)")); assertTrue(text.contains("2024-01-05")); assertTrue(text.contains("2024-09-30"))
    }

    @Test fun syncLogsAreKeptThirtyDays() {
        val now = ms("2026-10-22")
        assertEquals(now - 30L * 86_400_000, ArchivePolicy.syncLogCutoff(now))
    }
}
