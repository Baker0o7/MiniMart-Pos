package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Test

class BackupAdviceTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 1_800_000_000_000L

    @Test fun neverBackedUpIsNone() = assertEquals(BackupHealth.NONE, BackupAdvice.health(0, 0, false, now))
    @Test fun aRecentBackupIsGood() = assertEquals(BackupHealth.GOOD, BackupAdvice.health(now - day, 0, false, now))
    @Test fun anOldBackupIsStale() = assertEquals(BackupHealth.STALE, BackupAdvice.health(now - 4 * day, 0, false, now))
    @Test fun exactlyThreeDaysIsStillGood() = assertEquals(BackupHealth.GOOD, BackupAdvice.health(now - 3 * day, 0, false, now))

    @Test fun aFailureAfterTheLastGoodBackupIsFailed() =
        assertEquals(BackupHealth.FAILED, BackupAdvice.health(now - day, now - 1000, true, now))

    @Test fun aGoodBackupAfterAnOldFailureIsGood() =
        assertEquals(BackupHealth.GOOD, BackupAdvice.health(now - 1000, now - day, true, now))

    @Test fun agoSpeaksPlainEnglish() {
        assertEquals("just now", BackupAdvice.ago(now - 5_000, now))
        assertEquals("1 minute ago", BackupAdvice.ago(now - 60_000, now))
        assertEquals("5 minutes ago", BackupAdvice.ago(now - 5 * 60_000, now))
        assertEquals("1 hour ago", BackupAdvice.ago(now - 3_600_000, now))
        assertEquals("3 hours ago", BackupAdvice.ago(now - 3 * 3_600_000, now))
        assertEquals("yesterday", BackupAdvice.ago(now - day - 1000, now))
        assertEquals("4 days ago", BackupAdvice.ago(now - 4 * day, now))
        assertEquals("just now", BackupAdvice.ago(now + 10_000, now)) // a clock set slightly ahead
    }

    @Test fun passphraseStrength() {
        assertEquals(0, BackupAdvice.passphraseStrength(""))
        assertEquals(1, BackupAdvice.passphraseStrength("abc123"))
        assertEquals(1, BackupAdvice.passphraseStrength("aaaaaaaaaa"))
        assertEquals(2, BackupAdvice.passphraseStrength("shopbackup1"))
        assertEquals(3, BackupAdvice.passphraseStrength("Maziwa&Mkate2026"))
        assertEquals(3, BackupAdvice.passphraseStrength("correct horse battery"))
    }

    @Test fun humanSize() {
        assertEquals("512 B", BackupAdvice.humanSize(512))
        assertEquals("48 KB", BackupAdvice.humanSize(48 * 1024))
        assertEquals("2.5 MB", BackupAdvice.humanSize((2.5 * 1024 * 1024).toLong()))
    }
}
