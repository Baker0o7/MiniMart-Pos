package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackupPassphraseTest {
    @Test fun `a decent passphrase typed twice is accepted`() {
        assertNull(BackupPassphrase.problem("mango-River-42", "mango-River-42"))
        assertNull(BackupPassphrase.problem("Duka la Baker 2026", "Duka la Baker 2026"))
    }

    @Test fun `short ones are refused`() {
        assertEquals(BackupPassphrase.Problem.TOO_SHORT, BackupPassphrase.problem("abc123", "abc123"))
        assertEquals(BackupPassphrase.Problem.TOO_SHORT, BackupPassphrase.problem("", ""))
    }

    @Test fun `guessable ones are refused`() {
        listOf("12345678", "aaaaaaaa", "abababab", "password", "Password", "87654321", "abcdefgh", "MiniMart").forEach {
            assertEquals(BackupPassphrase.Problem.TOO_SIMPLE, BackupPassphrase.problem(it, it), "should refuse $it")
        }
    }

    @Test fun `a typo in the second box is caught because the backup cannot be opened without the right text`() {
        assertEquals(BackupPassphrase.Problem.MISMATCH, BackupPassphrase.problem("mango-River-42", "mango-River-24"))
        assertEquals(BackupPassphrase.Problem.MISMATCH, BackupPassphrase.problem("mango-River-42", "mango-river-42"))
    }
}

class SetupBackupStepTest {
    @Test fun `all three things are needed before setup can finish`() {
        assertTrue(SetupWizard.backupStepComplete(true, true, true))
        assertFalse(SetupWizard.backupStepComplete(false, true, true))
        assertFalse(SetupWizard.backupStepComplete(true, false, true))
        assertFalse(SetupWizard.backupStepComplete(true, true, false))
    }
}
