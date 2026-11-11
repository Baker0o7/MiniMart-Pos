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
        assertEquals("ساعتين", SyncFreshness.ageText(2 * 3_600_000L))
        assertEquals("5 دقائق", SyncFreshness.ageText(5 * 60_000L))
        val r = SyncFreshness.assess(true, 0L, 1_000L)!!
        assertTrue(r.headline.any { it in '؀'..'ۿ' })
        Lang.arabic = false
        assertEquals("2 hours", SyncFreshness.ageText(2 * 3_600_000L))
    }
}

class LangForecastTest {
    @AfterEach fun reset() { Lang.arabic = false }

    @Test fun `forecast accuracy sentence follows the language`() {
        val en = ForecastAccuracy(7, 10, 1.0).label()
        Lang.arabic = true
        val ar = ForecastAccuracy(7, 10, 1.0).label()
        assertTrue(en != ar && ar.any { it in '؀'..'ۿ' })
    }
}

class ArabicDigitsTest {
    @AfterEach fun reset() { Lang.arabic = false }

    @Test fun `off by default`() = assertEquals("KSh 1,234.50", ArabicDigits.show("KSh 1,234.50"))

    @Test fun `amounts counts and times use arabic digits`() {
        Lang.arabic = true
        assertEquals("KSh ١٬٢٣٤٫٥٠", ArabicDigits.show("KSh 1,234.50"))
        assertEquals("١٢:٣٠ - ١٥%", ArabicDigits.show("12:30 - 15%"))
    }

    @Test fun `codes and unit-glued numbers stay as they are so they still match and scan`() {
        Lang.arabic = true
        assertEquals("SJK4H2LMNO", ArabicDigits.show("SJK4H2LMNO"))
        assertEquals("٣ منتجات، 5kg", ArabicDigits.show("3 منتجات، 5kg"))

    }
}

class ArabicDigitsReadabilityTest {
    @AfterEach fun reset() { Lang.arabic = false }

    @Test fun `zero on its own stays a normal zero so it is not read as a bullet`() {
        Lang.arabic = true
        assertEquals("0 دفعة", ArabicDigits.show("0 دفعة"))
        assertEquals("KES 0.00", ArabicDigits.show("KES 0.00"))
        assertEquals("KES ١٠٬٦٦٩", ArabicDigits.show("KES 10,669"))
    }

    @Test fun `addresses and versions stay typeable`() {
        Lang.arabic = true
        assertEquals("192.168.1.5:9876", ArabicDigits.show("192.168.1.5:9876"))
        assertEquals("الإصدار 1.32.6", ArabicDigits.show("الإصدار 1.32.6"))
        assertEquals("١٢:٣٠", ArabicDigits.show("12:30"))
    }
}

class ArabicCountTest {
    @AfterEach fun reset() { Lang.arabic = false }
    private fun p(n: Int) = ArabicCount.phrase(n, "لا دفعات", "دفعة واحدة", "دفعتان", "دفعات", "دفعة")

    @Test fun `arabic noun follows the number`() {
        assertEquals("لا دفعات", p(0))
        assertEquals("دفعة واحدة", p(1))
        assertEquals("دفعتان", p(2))
        assertEquals("5 دفعات", p(5))
        assertEquals("11 دفعة", p(11))
    }

    @Test fun `english keeps one and many`() {
        assertEquals("1 payment", ArabicCount.t(1, "payment", "payments", "a", "b", "c", "d", "e"))
        assertEquals("0 payments", ArabicCount.t(0, "payment", "payments", "a", "b", "c", "d", "e"))
        Lang.arabic = true
        assertEquals("a", ArabicCount.t(0, "payment", "payments", "a", "b", "c", "d", "e"))
    }
}

class HourLabelTest {
    @AfterEach fun reset() { Lang.arabic = false }

    @Test fun `hours read as twelve hour clock in both languages`() {
        assertEquals("12 AM", HourLabel.of(0)); assertEquals("1 PM", HourLabel.of(13)); assertEquals("12 PM", HourLabel.of(12))
        Lang.arabic = true
        assertEquals("12 ص", HourLabel.of(0)); assertEquals("6 م", HourLabel.of(18))
    }
}
