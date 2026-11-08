package com.minimart.pos.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LangTest {
    @AfterEach fun reset() { Lang.arabic = false }

    private fun hasArabic(s: String) = s.any { it in '؀'..'ۿ' }

    @Test fun `english is the default`() {
        assertEquals("just now", BackupAdvice.ago(1_000, 1_000))
        assertEquals("Strong", BackupAdvice.strengthLabel(3))
    }

    @Test fun `backup ages read naturally in arabic`() {
        Lang.arabic = true
        val m = 60_000L
        assertEquals("الآن", BackupAdvice.ago(0, 10))
        assertEquals("منذ دقيقة", BackupAdvice.ago(0, m))
        assertEquals("منذ دقيقتين", BackupAdvice.ago(0, 2 * m))
        assertEquals("منذ 5 دقائق", BackupAdvice.ago(0, 5 * m))
        assertEquals("منذ 15 دقيقة", BackupAdvice.ago(0, 15 * m))
        assertEquals("أمس", BackupAdvice.ago(0, 30 * 60 * m))
        assertEquals("منذ 4 أيام", BackupAdvice.ago(0, 4 * 24 * 60 * m))
    }

    @Test fun `error advice is arabic, including the what-we-were-doing label`() {
        Lang.arabic = true
        val text = ErrorAdvice.explain(java.io.FileNotFoundException("x"), "Printing")
        assertTrue(text.startsWith("الطباعة لم ينجح"), text)
        assertTrue(!text.contains("didn't"), text)
        assertTrue(hasArabic(ErrorAdvice.reason(RuntimeException("boom"))))
    }

    @Test fun `health headline and checks are arabic`() {
        Lang.arabic = true
        val bad = listOf(HealthCheck("a", "Backup", HealthLevel.BAD, "x"))
        assertTrue(hasArabic(HardwareDiagnostics.headline(bad)))
        assertEquals("كل شيء سليم", HardwareDiagnostics.headline(emptyList()))
    }
}

class LangFreshnessTest {
    @AfterEach fun reset() { Lang.arabic = false }

    @Test fun `sync age and daily report switch language`() {
        Lang.arabic = true
        assertEquals("ساعتان", SyncFreshness.ageText(2 * 3_600_000L))
        assertEquals("5 دقائق", SyncFreshness.ageText(5 * 60_000L))
        val r = SyncFreshness.assess(true, 0L, 1_000L)!!
        assertTrue(r.headline.any { it in '؀'..'ۿ' })
        Lang.arabic = false
        assertEquals("2 hours", SyncFreshness.ageText(2 * 3_600_000L))
    }
}
