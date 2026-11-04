package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class ProblemLogTest {
    private fun entry(msg: String, doing: String = "Backup", at: Long = 1_000L, err: Throwable = IOException(msg)) =
        ProblemLog.entryFor(err, doing, at)

    @Test fun phoneNumbersCodesAndEmailsAreMaskedBeforeStoring() {
        val s = ProblemLog.scrub("send to 0712345678 code QWE123ABCD by a.b@shop.co.ke failed")
        assertFalse(s.contains("0712345678")); assertFalse(s.contains("QWE123ABCD")); assertFalse(s.contains("shop.co.ke"))
        assertTrue(s.contains("<digits>") && s.contains("<code>") && s.contains("<email>"))
    }

    @Test fun ordinaryWordsAndShortNumbersSurvive() {
        assertEquals("Sync failed after 3 tries", ProblemLog.scrub("Sync failed after 3 tries"))
    }

    @Test fun theSameProblemAgainOnlyBumpsItsCount() {
        var l = ProblemLog.add(emptyList(), entry("disk", at = 1))
        l = ProblemLog.add(l, entry("disk", at = 5))
        assertEquals(1, l.size); assertEquals(2, l[0].count); assertEquals(5L, l[0].lastAt)
    }

    @Test fun aDifferentProblemGoesFirstAndTheLogIsCapped() {
        var l = emptyList<ProblemEntry>()
        repeat(ProblemLog.MAX_ENTRIES + 10) { l = ProblemLog.add(l, entry("m$it", at = it.toLong())) }
        assertEquals(ProblemLog.MAX_ENTRIES, l.size)
        assertEquals("m${ProblemLog.MAX_ENTRIES + 9}", l.first().detail)
    }

    @Test fun businessRuleMessagesAndCancellationAreNotProblems() {
        assertTrue(ProblemLog.isExpected(IllegalArgumentException("That M-Pesa code is already used")))
        assertTrue(ProblemLog.isExpected(java.util.concurrent.CancellationException("x")))
        assertFalse(ProblemLog.isExpected(IllegalStateException()))
        assertFalse(ProblemLog.isExpected(IOException("disk")))
    }

    @Test fun savingAndReadingBackKeepsEverythingAndSkipsDamagedLines() {
        val l = listOf(entry("a", at = 2), entry("b", doing = "Sync", at = 1)).mapIndexed { i, e -> e.copy(count = i + 1) }
        val text = ProblemLog.encode(l) + "\nnot a real line\n9\tx\ty"
        assertEquals(l, ProblemLog.decode(text))
    }

    @Test fun onlyRecentProblemsAreCounted() {
        val day = 24L * 60 * 60 * 1000
        val l = listOf(entry("a", at = 10 * day), entry("b", at = 2 * day))
        assertEquals(1, ProblemLog.recent(l, now = 10 * day + 1))
    }

    @Test fun theReportListsEntriesAndSaysWhenThereAreNone() {
        assertTrue(ProblemLog.report(emptyList(), "MiniMart", { "t" }).endsWith("None recorded."))
        val r = ProblemLog.report(listOf(entry("disk full").copy(count = 3)), "MiniMart", { "T" })
        assertTrue(r.contains("T  Backup: IOException - disk full  (x3)"))
    }
}
