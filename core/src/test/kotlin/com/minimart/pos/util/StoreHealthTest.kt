package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StoreHealthTest {
    private fun level(checks: List<HealthCheck>, id: String) = checks.first { it.id == id }.level

    @Test fun healthyShopIsAllGood() {
        val checks = StoreHealth.evaluate(HealthInputs(dataProblems = 0, lastBackupAgeDays = 0, backupEverMade = true, freeStorageMb = 4000))
        assertEquals(HealthLevel.GOOD, StoreHealth.overall(checks))
        assertEquals("Your shop's data looks healthy", StoreHealth.headline(checks))
    }

    @Test fun neverBackedUpIsBad() {
        val checks = StoreHealth.evaluate(HealthInputs(dataProblems = 0, backupEverMade = false))
        assertEquals(HealthLevel.BAD, level(checks, "backup"))
        assertEquals(HealthAction.BACKUP, checks.first { it.id == "backup" }.action)
    }

    @Test fun backupAgeThresholds() {
        fun lv(days: Int) = level(StoreHealth.evaluate(HealthInputs(backupEverMade = true, lastBackupAgeDays = days)), "backup")
        assertEquals(HealthLevel.GOOD, lv(2))
        assertEquals(HealthLevel.WARN, lv(3))
        assertEquals(HealthLevel.WARN, lv(13))
        assertEquals(HealthLevel.BAD, lv(14))
    }

    @Test fun uncheckedDataIsOnlyAWarning() {
        assertEquals(HealthLevel.WARN, level(StoreHealth.evaluate(HealthInputs(dataProblems = null)), "data"))
        assertEquals(HealthLevel.BAD, level(StoreHealth.evaluate(HealthInputs(dataProblems = 3)), "data"))
    }

    @Test fun worstProblemsComeFirst() {
        val checks = StoreHealth.evaluate(HealthInputs(
            dataProblems = 0, backupEverMade = true, lastBackupAgeDays = 0, lowStock = 4, expired = 2, freeStorageMb = 4000))
        assertEquals(HealthLevel.BAD, checks.first().level)
        val levels = checks.map { it.level.ordinal }
        assertEquals(levels.sortedDescending(), levels)
    }

    @Test fun syncRowAppearsOnlyWhenRelevant() {
        assertTrue(StoreHealth.evaluate(HealthInputs()).none { it.id == "sync" })
        assertEquals(HealthLevel.BAD, level(StoreHealth.evaluate(HealthInputs(refusedSync = 2)), "sync"))
        assertEquals(HealthLevel.WARN, level(StoreHealth.evaluate(HealthInputs(syncConfigured = true, pendingSync = 50)), "sync"))
        assertEquals(HealthLevel.GOOD, level(StoreHealth.evaluate(HealthInputs(syncConfigured = true, pendingSync = 3)), "sync"))
    }

    @Test fun lowStorageIsFlagged() {
        assertEquals(HealthLevel.BAD, level(StoreHealth.evaluate(HealthInputs(freeStorageMb = 40)), "storage"))
        assertEquals(HealthLevel.WARN, level(StoreHealth.evaluate(HealthInputs(freeStorageMb = 300)), "storage"))
    }

    @Test fun securityRowsAppearWhenNeeded() {
        val ids = StoreHealth.evaluate(HealthInputs(secureStorageAvailable = false, weakPairingCode = true, defaultPinStillUsed = true)).map { it.id }
        assertTrue(ids.containsAll(listOf("secure", "pairing", "pin")))
    }
}
