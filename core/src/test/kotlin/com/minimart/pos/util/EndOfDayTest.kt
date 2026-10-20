package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndOfDayTest {
    @Test fun quietCleanDayCanFinishWithOnlyBackupLeft() {
        val steps = EndOfDay.steps(EndOfDayInputs(salesToday = 0))
        assertTrue(EndOfDay.canFinish(steps))
        assertEquals(listOf("backup"), steps.filter { it.state != StepState.DONE }.map { it.id })
    }

    @Test fun openShiftBlocksFinishing() {
        val steps = EndOfDay.steps(EndOfDayInputs(openShifts = listOf("Amina", "Juma")))
        assertFalse(EndOfDay.canFinish(steps))
        val s = steps.first { it.id == "shifts" }
        assertEquals(StepState.BLOCKED, s.state)
        assertTrue(s.detail.contains("Amina, Juma"))
    }

    @Test fun syncStepOnlyShownWhenSyncIsSetUp() {
        assertTrue(EndOfDay.steps(EndOfDayInputs()).none { it.id == "sync" })
        val s = EndOfDay.steps(EndOfDayInputs(syncConfigured = true, pendingSync = 4)).first { it.id == "sync" }
        assertEquals(StepState.TODO, s.state)
    }

    @Test fun expenseReminderOnlyWhenThereWereSales() {
        assertTrue(EndOfDay.steps(EndOfDayInputs(salesToday = 0)).none { it.id == "expenses" })
        assertTrue(EndOfDay.steps(EndOfDayInputs(salesToday = 5)).any { it.id == "expenses" })
        assertTrue(EndOfDay.steps(EndOfDayInputs(salesToday = 5, expensesRecordedToday = true)).none { it.id == "expenses" })
    }

    @Test fun remainingCountsEverythingNotDone() {
        val steps = EndOfDay.steps(EndOfDayInputs(heldCarts = 2, unclaimedMpesaToday = 1, backupDoneToday = true))
        assertEquals(2, EndOfDay.remaining(steps))
    }
}
