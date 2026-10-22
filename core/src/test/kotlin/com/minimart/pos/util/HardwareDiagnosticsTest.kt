package com.minimart.pos.util

import org.junit.Assert.*
import org.junit.Test

class HardwareDiagnosticsTest {
    private fun find(i: HardwareInputs, id: String) = HardwareDiagnostics.evaluate(i).first { it.id == id }
    private val healthy = HardwareInputs(bluetoothEnabled = true, printerChosen = true, printerName = "XP-58", printerPaired = true,
        networkConnected = true, onWifi = true, freeStorageMb = 4000, batteryPercent = 80)

    @Test fun healthyPhoneHasNoProblems() {
        val checks = HardwareDiagnostics.evaluate(healthy)
        assertTrue(checks.none { it.level != HealthLevel.GOOD })
        assertEquals("Everything checks out", HardwareDiagnostics.headline(checks))
    }

    @Test fun bluetoothProblemsAreExplained() {
        assertEquals(HealthLevel.BAD, find(healthy.copy(bluetoothEnabled = false), "bluetooth").level)
        assertEquals(HealthLevel.BAD, find(healthy.copy(bluetoothPermission = false), "bluetooth").level)
        assertEquals(HealthLevel.WARN, find(healthy.copy(bluetoothSupported = false), "bluetooth").level)
        assertEquals(HealthLevel.WARN, find(healthy.copy(bluetoothEnabled = null), "bluetooth").level)
    }

    @Test fun printerStates() {
        assertEquals(HealthLevel.WARN, find(healthy.copy(printerChosen = false), "printer").level)
        assertEquals(HealthLevel.BAD, find(healthy.copy(printerPaired = false), "printer").level)
        val failed = find(healthy.copy(printerTest = TestOutcome.Failed("Printer not connected")), "printer")
        assertEquals(HealthLevel.BAD, failed.level); assertTrue(failed.detail.contains("Printer not connected"))
        assertTrue(find(healthy.copy(printerTest = TestOutcome.Passed), "printer").detail.contains("printed the test page"))
        assertTrue(find(healthy, "printer").detail.contains("Test print"))
    }

    @Test fun drawerStates() {
        assertEquals(HealthLevel.BAD, find(healthy.copy(drawerTest = TestOutcome.Failed("x")), "drawer").level)
        assertEquals(HealthLevel.GOOD, find(healthy.copy(drawerTest = TestOutcome.Passed), "drawer").level)
        assertTrue(find(healthy.copy(printerChosen = false), "drawer").detail.contains("nothing to open"))
    }

    @Test fun cameraAndSmsPermissions() {
        assertEquals(HealthLevel.BAD, find(healthy.copy(cameraPermission = false), "camera").level)
        assertEquals(HealthLevel.WARN, find(healthy.copy(cameraPresent = false), "camera").level)
        assertEquals(HealthLevel.GOOD, find(healthy.copy(smsTrackingOn = false, smsPermission = false), "sms").level)
        assertEquals(HealthLevel.BAD, find(healthy.copy(smsTrackingOn = true, smsPermission = false), "sms").level)
    }

    @Test fun storageBatteryAndNetworkThresholds() {
        assertEquals(HealthLevel.BAD, find(healthy.copy(freeStorageMb = 50), "storage").level)
        assertEquals(HealthLevel.WARN, find(healthy.copy(freeStorageMb = 300), "storage").level)
        assertEquals(HealthLevel.BAD, find(healthy.copy(batteryPercent = 10), "battery").level)
        assertEquals(HealthLevel.GOOD, find(healthy.copy(batteryPercent = 10, charging = true), "battery").level)
        assertEquals(HealthLevel.WARN, find(healthy.copy(networkConnected = false), "network").level)
        assertTrue(find(healthy.copy(onWifi = false), "network").detail.contains("same Wi-Fi"))
    }

    @Test fun worstProblemsComeFirst() {
        val checks = HardwareDiagnostics.evaluate(healthy.copy(freeStorageMb = 10, networkConnected = false))
        assertEquals(HealthLevel.BAD, checks.first().level)
        assertEquals(checks.map { it.level.ordinal }.sortedDescending(), checks.map { it.level.ordinal })
        assertEquals(HealthLevel.BAD, HardwareDiagnostics.overall(checks))
        assertTrue(HardwareDiagnostics.headline(checks).startsWith("1 thing"))
    }

    @Test fun hugeTextIsFlagged() {
        assertEquals(HealthLevel.WARN, find(healthy.copy(fontScale = 2f), "display").level)
        assertEquals(HealthLevel.GOOD, find(healthy.copy(fontScale = 1.3f), "display").level)
    }
}
