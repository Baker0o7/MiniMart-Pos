package com.minimart.pos.util

/** Outcome of a test the person ran on the diagnostics screen (a test print, a drawer kick). */
sealed class TestOutcome {
    object Passed : TestOutcome()
    data class Failed(val reason: String) : TestOutcome()
}

/** What the phone and its accessories report. A null means "could not be read" (or does not apply). */
data class HardwareInputs(
    val bluetoothSupported: Boolean = true,
    val bluetoothEnabled: Boolean? = null,
    val bluetoothPermission: Boolean = true,
    val printerChosen: Boolean = false,
    val printerName: String? = null,
    /** Whether the chosen printer is still in the phone's paired list. Null when that cannot be read. */
    val printerPaired: Boolean? = null,
    val printerTest: TestOutcome? = null,
    val drawerConfigured: Boolean = false,
    val drawerTest: TestOutcome? = null,
    val cameraPresent: Boolean = true,
    val cameraPermission: Boolean = true,
    val flashPresent: Boolean = false,
    val smsTrackingOn: Boolean = false,
    val smsPermission: Boolean = true,
    val notificationsAllowed: Boolean = true,
    val networkConnected: Boolean? = null,
    val onWifi: Boolean? = null,
    val freeStorageMb: Long? = null,
    val batteryPercent: Int? = null,
    val charging: Boolean = false,
    val fontScale: Float = 1f,
    val screenWidthDp: Int = 360
)

/**
 * Plain-language checks of the printer, drawer, scanner and the phone itself. Pure, so it is unit-tested;
 * the app gathers [HardwareInputs] and shows the result. Worst problems come first.
 */
object HardwareDiagnostics {
    fun evaluate(i: HardwareInputs): List<HealthCheck> {
        val out = mutableListOf<HealthCheck>()

        out += when {
            !i.bluetoothSupported -> HealthCheck("bluetooth", "Bluetooth", HealthLevel.WARN,
                "This device has no Bluetooth, so a Bluetooth printer or scanner cannot be used. A USB or keyboard-style scanner still works.")
            !i.bluetoothPermission -> HealthCheck("bluetooth", "Bluetooth", HealthLevel.BAD,
                "The app is not allowed to use Bluetooth. Allow \"Nearby devices\" in the phone's app settings.")
            i.bluetoothEnabled == false -> HealthCheck("bluetooth", "Bluetooth", HealthLevel.BAD,
                "Bluetooth is switched off. Turn it on to reach the printer.")
            i.bluetoothEnabled == null -> HealthCheck("bluetooth", "Bluetooth", HealthLevel.WARN, "Bluetooth state could not be read.")
            else -> HealthCheck("bluetooth", "Bluetooth", HealthLevel.GOOD, "On and allowed.")
        }

        out += printer(i)
        out += drawer(i)

        out += when {
            !i.cameraPresent -> HealthCheck("camera", "Camera scanner", HealthLevel.WARN,
                "No camera found. Use a Bluetooth or keyboard-style scanner, or type barcodes in.")
            !i.cameraPermission -> HealthCheck("camera", "Camera scanner", HealthLevel.BAD,
                "The app is not allowed to use the camera, so it cannot scan barcodes. Allow Camera in the phone's app settings.")
            else -> HealthCheck("camera", "Camera scanner", HealthLevel.GOOD,
                if (i.flashPresent) "Camera allowed. Flash available for dark shops." else "Camera allowed. No flash on this device.")
        }

        out += when {
            !i.smsTrackingOn -> HealthCheck("sms", "M-Pesa messages", HealthLevel.GOOD, "Reading M-Pesa texts is off. Turn it on in Settings to log payments automatically.")
            !i.smsPermission -> HealthCheck("sms", "M-Pesa messages", HealthLevel.BAD,
                "Tracking is on but the app is not allowed to read texts, so payments will not be logged. Allow SMS in the phone's app settings.")
            else -> HealthCheck("sms", "M-Pesa messages", HealthLevel.GOOD, "Allowed. New M-Pesa texts are logged as they arrive.")
        }

        out += if (i.notificationsAllowed) HealthCheck("notifications", "Alerts", HealthLevel.GOOD, "Low-stock and expiry alerts can be shown.")
        else HealthCheck("notifications", "Alerts", HealthLevel.WARN,
            "Notifications are blocked, so low-stock and expiry alerts will not appear. Allow them in the phone's app settings.")

        out += when {
            i.networkConnected == null -> HealthCheck("network", "Network", HealthLevel.WARN, "Network state could not be read.")
            !i.networkConnected -> HealthCheck("network", "Network", HealthLevel.WARN,
                "No connection. Selling works offline; syncing, the cloud dashboard and M-Pesa prompts need a connection.")
            i.onWifi == false -> HealthCheck("network", "Network", HealthLevel.GOOD,
                "Connected by mobile data. Syncing between phones needs both on the same Wi-Fi.")
            else -> HealthCheck("network", "Network", HealthLevel.GOOD, "Connected by Wi-Fi.")
        }

        out += when {
            i.freeStorageMb == null -> HealthCheck("storage", "Phone storage", HealthLevel.WARN, "Free space could not be read.")
            i.freeStorageMb < 100 -> HealthCheck("storage", "Phone storage", HealthLevel.BAD,
                "Only ${i.freeStorageMb} MB free. Sales may fail to save. Free some space.")
            i.freeStorageMb < 500 -> HealthCheck("storage", "Phone storage", HealthLevel.WARN, "${i.freeStorageMb} MB free. Getting low.")
            else -> HealthCheck("storage", "Phone storage", HealthLevel.GOOD, "${i.freeStorageMb} MB free.")
        }

        out += when {
            i.batteryPercent == null -> HealthCheck("battery", "Battery", HealthLevel.GOOD, "Battery level not available.")
            i.charging -> HealthCheck("battery", "Battery", HealthLevel.GOOD, "${i.batteryPercent}% and charging.")
            i.batteryPercent < 15 -> HealthCheck("battery", "Battery", HealthLevel.BAD, "${i.batteryPercent}%. Plug in before you stop being able to sell.")
            i.batteryPercent < 30 -> HealthCheck("battery", "Battery", HealthLevel.WARN, "${i.batteryPercent}%. Keep a charger close.")
            else -> HealthCheck("battery", "Battery", HealthLevel.GOOD, "${i.batteryPercent}%.")
        }

        out += when {
            i.fontScale >= 1.5f -> HealthCheck("display", "Screen text size", HealthLevel.WARN,
                "Text is set very large (${percent(i.fontScale)}). The app adapts, but some screens may need scrolling.")
            else -> HealthCheck("display", "Screen text size", HealthLevel.GOOD,
                "Text at ${percent(i.fontScale)} on a ${i.screenWidthDp} dp wide screen.")
        }

        return out.sortedBy { it.level.ordinal.let { o -> 2 - o } }   // BAD first, then WARN, then GOOD (stable)
    }

    private fun printer(i: HardwareInputs): HealthCheck = when {
        !i.printerChosen -> HealthCheck("printer", "Receipt printer", HealthLevel.WARN,
            "No printer chosen. Pick one in Settings to print receipts. Digital receipts still work.")
        i.printerPaired == false -> HealthCheck("printer", "Receipt printer", HealthLevel.BAD,
            "${name(i.printerName)} is no longer paired with this phone. Pair it again in the phone's Bluetooth settings.")
        i.printerTest is TestOutcome.Failed -> HealthCheck("printer", "Receipt printer", HealthLevel.BAD,
            "Test print failed: ${i.printerTest.reason}")
        i.printerTest == TestOutcome.Passed -> HealthCheck("printer", "Receipt printer", HealthLevel.GOOD,
            "${name(i.printerName)} printed the test page.")
        else -> HealthCheck("printer", "Receipt printer", HealthLevel.GOOD,
            "${name(i.printerName)} is paired. Tap Test print to be sure it works.")
    }

    private fun drawer(i: HardwareInputs): HealthCheck = when {
        i.drawerTest is TestOutcome.Failed -> HealthCheck("drawer", "Cash drawer", HealthLevel.BAD,
            "The drawer did not open: ${i.drawerTest.reason}")
        i.drawerTest == TestOutcome.Passed -> HealthCheck("drawer", "Cash drawer", HealthLevel.GOOD,
            "The open signal was sent. If the drawer stayed shut, try the other pin in Settings.")
        i.drawerConfigured || i.printerChosen -> HealthCheck("drawer", "Cash drawer", HealthLevel.GOOD,
            "Tap Test drawer to send the open signal.")
        else -> HealthCheck("drawer", "Cash drawer", HealthLevel.GOOD, "No printer or drawer set up, so there is nothing to open.")
    }

    private fun name(n: String?) = n?.takeIf { it.isNotBlank() } ?: "The printer"
    private fun percent(scale: Float) = "${(scale * 100).toInt()}%"

    /** Worst level in a list; GOOD when empty. */
    fun overall(checks: List<HealthCheck>): HealthLevel = StoreHealth.overall(checks)

    fun headline(checks: List<HealthCheck>): String {
        val bad = checks.count { it.level == HealthLevel.BAD }
        val warn = checks.count { it.level == HealthLevel.WARN }
        return when {
            bad > 0 -> "$bad thing(s) to fix"
            warn > 0 -> "Working, with $warn thing(s) to look at"
            else -> "Everything checks out"
        }
    }
}
