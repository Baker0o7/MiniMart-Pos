package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ResilienceTest {
    private val H = 3_600_000L
    private val now = 1_000_000_000_000L

    // ── Sync freshness ──
    @Test fun aPhoneThatDoesNotSyncHasNothingToBeStale() = assertNull(SyncFreshness.assess(false, 0, now))
    @Test fun neverSyncedIsAWarningAndWarnsAtCheckout() {
        val r = SyncFreshness.assess(true, 0, now)!!; assertEquals(Freshness.NEVER, r.freshness); assertTrue(r.warnAtCheckout)
    }
    @Test fun freshAgingAndStaleBoundaries() {
        assertEquals(Freshness.FRESH, SyncFreshness.assess(true, now - 11 * H, now)!!.freshness)
        assertEquals(Freshness.AGING, SyncFreshness.assess(true, now - 12 * H, now)!!.freshness)
        assertEquals(Freshness.AGING, SyncFreshness.assess(true, now - 47 * H, now)!!.freshness)
        assertEquals(Freshness.STALE, SyncFreshness.assess(true, now - 48 * H, now)!!.freshness)
        assertTrue(SyncFreshness.assess(true, now - 48 * H, now)!!.warnAtCheckout)
        assertFalse(SyncFreshness.assess(true, now - 20 * H, now)!!.warnAtCheckout)
    }
    @Test fun aClockThatWentBackwardsDoesNotBreakIt() = assertEquals(Freshness.FRESH, SyncFreshness.assess(true, now + 5 * H, now)!!.freshness)
    @Test fun conflictsAreMentioned() = assertTrue(SyncFreshness.assess(true, now - H, now, openConflicts = 2)!!.detail.contains("2 disagreement"))
    @Test fun ageIsPlainLanguage() {
        assertEquals("less than a minute", SyncFreshness.ageText(5_000)); assertEquals("1 minute", SyncFreshness.ageText(60_000))
        assertEquals("1 hour", SyncFreshness.ageText(H)); assertEquals("30 hours", SyncFreshness.ageText(30 * H)); assertEquals("3 days", SyncFreshness.ageText(72 * H))
    }
    @Test fun healthShowsStaleDataOnlyWhenSyncIsSetUp() {
        assertTrue(StoreHealth.evaluate(HealthInputs(syncConfigured = true, lastSyncAgeHours = 60)).any { it.id == "sync_stale" && it.level == HealthLevel.BAD })
        assertTrue(StoreHealth.evaluate(HealthInputs(syncConfigured = true, lastSyncAgeHours = 20)).any { it.id == "sync_stale" && it.level == HealthLevel.WARN })
        assertTrue(StoreHealth.evaluate(HealthInputs(syncConfigured = true, lastSyncAgeHours = 2)).none { it.id == "sync_stale" })
        assertTrue(StoreHealth.evaluate(HealthInputs(syncConfigured = false, lastSyncAgeHours = 99)).none { it.id == "sync_stale" })
        assertTrue(StoreHealth.evaluate(HealthInputs(syncConflicts = 3)).any { it.id == "sync_conflicts" })
    }

    // ── Printer history ──
    @Test fun failuresAreCountedForTheLastDayOnly() {
        var r = PrinterRecord()
        r = PrinterHistory.onFailure(r, now - 30 * H); r = PrinterHistory.onFailure(r, now - 2 * H); r = PrinterHistory.onFailure(r, now - H)
        assertEquals(2, PrinterHistory.failuresInLastDay(r, now))
    }
    @Test fun oldFailuresAreForgottenAndTheListIsBounded() {
        var r = PrinterRecord(failures = listOf(now - 30L * 24 * H))
        repeat(40) { r = PrinterHistory.onFailure(r, now + it) }
        assertEquals(PrinterHistory.MAX, r.failures.size); assertTrue(r.failures.all { it >= now })
    }
    @Test fun hoursSinceTheLastGoodPrint() {
        assertNull(PrinterHistory.hoursSinceOk(PrinterRecord(), now))
        assertEquals(5L, PrinterHistory.hoursSinceOk(PrinterHistory.onSuccess(PrinterRecord(), now - 5 * H), now))
    }
    @Test fun recordSurvivesSavingAndBadText() {
        val r = PrinterRecord(123L, listOf(1L, 2L, 3L)); assertEquals(r, PrinterHistory.decode(PrinterHistory.encode(r)))
        assertEquals(PrinterRecord(), PrinterHistory.decode(null)); assertEquals(PrinterRecord(), PrinterHistory.decode("garbage;x,y"))
    }
    @Test fun repeatedFailuresShowAsAHardwareWarningWithAFallback() {
        val c = HardwareDiagnostics.evaluate(HardwareInputs(printerChosen = true, printerPaired = true, printerFailuresToday = 3, printerLastOkHoursAgo = 4)).first { it.id == "printer" }
        assertEquals(HealthLevel.WARN, c.level); assertTrue(c.detail.contains("PDF")); assertTrue(c.detail.contains("4 hour"))
        assertEquals(HealthLevel.BAD, HardwareDiagnostics.evaluate(HardwareInputs(printerChosen = true, printerPaired = true, printerFailuresToday = 5)).first { it.id == "printer" }.level)
        assertEquals(HealthLevel.GOOD, HardwareDiagnostics.evaluate(HardwareInputs(printerChosen = true, printerPaired = true, printerFailuresToday = 1)).first { it.id == "printer" }.level)
    }

    // ── Crash loop ──
    @Test fun threeStartsInFiveMinutesIsALoop() {
        var s = emptyList<Long>(); for (t in listOf(0L, 60_000L)) s = CrashLoopGuard.record(s, now + t)
        assertFalse(CrashLoopGuard.inLoop(s, now + 60_000)); s = CrashLoopGuard.record(s, now + 120_000)
        assertTrue(CrashLoopGuard.inLoop(s, now + 120_000)); assertEquals(CrashLoopGuard.HOLD_BACK_MS, CrashLoopGuard.holdBackMs(true)); assertEquals(0L, CrashLoopGuard.holdBackMs(false))
    }
    @Test fun spreadOutStartsAreNotALoop() {
        var s = emptyList<Long>(); for (m in 0..5) s = CrashLoopGuard.record(s, now + m * 10 * 60_000L)
        assertFalse(CrashLoopGuard.inLoop(s, now + 50 * 60_000L))
    }
    @Test fun startListSavesAndTolerantlyLoads() {
        assertEquals(listOf(1L, 2L), CrashLoopGuard.decode(CrashLoopGuard.encode(listOf(1L, 2L)))); assertEquals(emptyList<Long>(), CrashLoopGuard.decode("x,,y"))
    }
    @Test fun crashLoopShowsInHealth() = assertTrue(StoreHealth.evaluate(HealthInputs(crashLoop = true)).any { it.id == "crash_loop" && it.level == HealthLevel.BAD })

    // ── Forecast accuracy ──
    @Test fun aSteadySellerIsForecastAccurately() {
        val a = DemandForecast.accuracy(List(35) { 10.0 }, 0)!!
        assertEquals(0, a.percentOff); assertTrue(a.label().contains("accurate"))
    }
    @Test fun aWildSellerIsFlaggedAsHardToPredict() {
        val h = List(28) { if (it % 2 == 0) 0.0 else 40.0 }
        val a = DemandForecast.accuracy(h, 0)!!; assertTrue(a.percentOff > 35); assertTrue(a.label().contains("hard to predict"))
    }
    @Test fun tooLittleHistoryOrNoRecentSalesGivesNoScore() {
        assertNull(DemandForecast.accuracy(List(10) { 5.0 }, 0)); assertNull(DemandForecast.accuracy(List(20) { 5.0 } + List(7) { 0.0 }, 0))
    }
}
