package com.minimart.pos.util

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class ObservabilityTest {
    @AfterEach fun reset() { ErrorAdvice.onProblem = null }

    private fun level(i: HealthInputs, id: String) = StoreHealth.evaluate(i).firstOrNull { it.id == id }?.level

    @Test fun unexpectedErrorsReachTheLogButRuleMessagesDoNot() {
        val seen = mutableListOf<String>()
        ErrorAdvice.onProblem = { e, doing -> seen += "$doing:${e::class.java.simpleName}" }
        ErrorAdvice.explain(IOException("disk"), "Backup")
        ErrorAdvice.explain(IllegalArgumentException("That code is already used"), "Checkout")
        assertEquals(listOf("Backup:IOException"), seen)
    }

    @Test fun aBrokenLoggerCannotBreakTheMessage() {
        ErrorAdvice.onProblem = { _, _ -> throw IllegalStateException("log failed") }
        assertTrue(ErrorAdvice.explain(IOException("x"), "Backup").startsWith("Backup didn't work."))
    }

    @Test fun problemsRowAppearsOnlyWhenThereAreSome() {
        assertNull(level(HealthInputs(), "problems"))
        assertEquals(HealthLevel.WARN, level(HealthInputs(recentProblems = 3), "problems"))
        assertEquals(HealthLevel.BAD, level(HealthInputs(recentProblems = 25), "problems"))
    }

    @Test fun dataProtectionRowAppearsOnlyWhenTheDatabaseIsKnownToBePlain() {
        assertNull(level(HealthInputs(), "dbenc"))
        assertNull(level(HealthInputs(databaseEncrypted = true), "dbenc"))
        assertEquals(HealthLevel.WARN, level(HealthInputs(databaseEncrypted = false), "dbenc"))
    }
}

class DeviceErrorTest {
    @Test fun aDroppedBluetoothLinkIsExplainedAsThePrinterNotAnswering() {
        val m = ErrorAdvice.explainDevice(IOException("read failed, socket might closed or timeout"), "Printing", "printer")
        assertTrue(m.startsWith("Printing didn't work."))
        assertTrue(m.contains("printer did not respond"))
        assertTrue(!m.contains("socket"))
    }

    @Test fun aMissingPermissionStillTellsThemToAllowIt() {
        assertTrue(ErrorAdvice.explainDevice(SecurityException("bt"), "Printing", "printer").contains("Permissions"))
    }
}
