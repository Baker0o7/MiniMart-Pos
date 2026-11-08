package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LockoutClockTest {
    private val dur = 60_000L

    @Test fun noLockoutMeansZero() = assertEquals(0L, LockoutClock.remainingMs(0, 0, -1, 1000, 1000))

    @Test fun countsDownWithTheMonotonicClock() {
        assertEquals(40_000L, LockoutClock.remainingMs(untilWall = 160_000, durationMs = dur, startElapsed = 500_000, nowWall = 120_000, nowElapsed = 520_000))
    }

    @Test fun movingTheWallClockForwardDoesNotEndTheLockout() {
        // 10 s really passed, but the wall clock was set a day ahead.
        val r = LockoutClock.remainingMs(untilWall = 160_000, durationMs = dur, startElapsed = 500_000, nowWall = 160_000 + 86_400_000, nowElapsed = 510_000)
        assertEquals(50_000L, r)
    }

    @Test fun movingTheWallClockBackDoesNotExtendIt() {
        val r = LockoutClock.remainingMs(untilWall = 160_000, durationMs = dur, startElapsed = 500_000, nowWall = 0, nowElapsed = 510_000)
        assertEquals(50_000L, r)
    }

    @Test fun finishedLockoutIsZero() {
        assertEquals(0L, LockoutClock.remainingMs(160_000, dur, 500_000, 400_000, 700_000))
    }

    @Test fun afterARestartTheWallClockIsUsedButCappedAtTheLockoutLength() {
        // elapsed clock restarted (now < start). Wall clock says 1 hour remains: cap to 60 s.
        assertEquals(dur, LockoutClock.remainingMs(untilWall = 4_000_000, durationMs = dur, startElapsed = 900_000, nowWall = 400_000, nowElapsed = 5_000))
        assertEquals(20_000L, LockoutClock.remainingMs(untilWall = 160_000, durationMs = dur, startElapsed = 900_000, nowWall = 140_000, nowElapsed = 5_000))
    }

    @Test fun oldRecordsWithoutMonotonicReadingUseTheWallClock() {
        assertEquals(30_000L, LockoutClock.remainingMs(untilWall = 130_000, durationMs = 0, startElapsed = -1, nowWall = 100_000, nowElapsed = 1))
    }
}
