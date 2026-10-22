package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test

class SupportInfoTest {
    private val facts = SupportFacts("1.18.0", 105, "14", 34, "Tecno SPARK 10", "en_KE", 2048, 0, 2, syncOn = true, cloudOn = false, instantMpesaOn = true)

    @Test fun reportListsTheUsefulFacts() {
        val t = SupportInfo.format(facts)
        assertTrue(t.contains("MiniMart POS 1.18.0 (build 105)"))
        assertTrue(t.contains("Android 14 (API 34)")); assertTrue(t.contains("Device: Tecno SPARK 10"))
        assertTrue(t.contains("Free storage: 2048 MB")); assertTrue(t.contains("Last backup: 2 day(s) ago"))
        assertTrue(t.contains("Features on: sync, instant M-Pesa"))
    }

    @Test fun missingValuesReadSensibly() {
        val t = SupportInfo.format(facts.copy(freeStorageMb = null, lastBackupDays = null, syncOn = false, instantMpesaOn = false))
        assertTrue(t.contains("Free storage: unknown")); assertTrue(t.contains("Last backup: none found")); assertTrue(t.endsWith("Features on: none"))
        assertTrue(SupportInfo.format(facts.copy(lastBackupDays = 0)).contains("Last backup: today"))
    }

    @Test fun oddDeviceNamesAreCleaned() {
        assertEquals("unknown", SupportInfo.clean("\u0000‮"))
        assertEquals("Phone X", SupportInfo.clean("  Phone\nX\u0007 ".replace("\n", " ")))
        assertEquals(60, SupportInfo.clean("a".repeat(500)).length)
    }

    @Test fun reportHoldsNoPersonalOrSecretFields() {
        val names = SupportFacts::class.java.declaredFields.map { it.name.lowercase() }
        listOf("phone", "customer", "secret", "token", "key", "sale", "name").forEach { bad -> assertTrue(bad, names.none { it.contains(bad) }) }
    }
}
