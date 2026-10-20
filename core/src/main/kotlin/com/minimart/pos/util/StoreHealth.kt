package com.minimart.pos.util

enum class HealthLevel { GOOD, WARN, BAD }

/** Where the "fix it" button on a health row should go. */
enum class HealthAction { NONE, BACKUP, SYNC, MPESA, LOW_STOCK, SETTINGS, DATA_CHECK, SHIFTS, USERS }

data class HealthCheck(
    val id: String,
    val title: String,
    val level: HealthLevel,
    val detail: String,
    val action: HealthAction = HealthAction.NONE
)

/** Everything the health screen can know, gathered by the app. A null means "could not be read". */
data class HealthInputs(
    val dataProblems: Int? = null,
    val lastBackupAgeDays: Int? = null,
    val backupEverMade: Boolean = false,
    val pendingSync: Int = 0,
    val refusedSync: Int = 0,
    val syncConfigured: Boolean = false,
    val freeStorageMb: Long? = null,
    val unclaimedMpesaToday: Int = 0,
    val lowStock: Int = 0,
    val expired: Int = 0,
    val crashReports: Int = 0,
    val secureStorageAvailable: Boolean = true,
    val weakPairingCode: Boolean = false,
    val defaultPinStillUsed: Boolean = false,
    val staleOpenShifts: Int = 0
)

/** Turns raw numbers into plain-language checks, worst problems first. Pure, so it can be tested. */
object StoreHealth {
    fun evaluate(i: HealthInputs): List<HealthCheck> {
        val out = mutableListOf<HealthCheck>()

        out += when {
            i.dataProblems == null -> HealthCheck("data", "Sales and stock records", HealthLevel.WARN,
                "Not checked yet. Run the check to make sure totals and stock add up.", HealthAction.DATA_CHECK)
            i.dataProblems == 0 -> HealthCheck("data", "Sales and stock records", HealthLevel.GOOD, "Everything adds up.")
            else -> HealthCheck("data", "Sales and stock records", HealthLevel.BAD,
                "${i.dataProblems} record(s) do not add up. Open the check to see which.", HealthAction.DATA_CHECK)
        }

        out += when {
            !i.backupEverMade -> HealthCheck("backup", "Backup", HealthLevel.BAD,
                "No backup has been made on this phone. If it is lost or broken, your sales history goes with it.", HealthAction.BACKUP)
            i.lastBackupAgeDays == null -> HealthCheck("backup", "Backup", HealthLevel.WARN,
                "A backup exists but its date could not be read.", HealthAction.BACKUP)
            i.lastBackupAgeDays >= 14 -> HealthCheck("backup", "Backup", HealthLevel.BAD,
                "Last backup was ${i.lastBackupAgeDays} days ago.", HealthAction.BACKUP)
            i.lastBackupAgeDays >= 3 -> HealthCheck("backup", "Backup", HealthLevel.WARN,
                "Last backup was ${i.lastBackupAgeDays} days ago.", HealthAction.BACKUP)
            else -> HealthCheck("backup", "Backup", HealthLevel.GOOD,
                if (i.lastBackupAgeDays == 0) "Backed up today." else "Backed up ${i.lastBackupAgeDays} day(s) ago.")
        }

        if (i.syncConfigured || i.pendingSync > 0 || i.refusedSync > 0) {
            out += when {
                i.refusedSync > 0 -> HealthCheck("sync", "Till sync", HealthLevel.BAD,
                    "${i.refusedSync} sale(s) were refused by the main phone and exist only on this one.", HealthAction.SYNC)
                i.pendingSync > 20 -> HealthCheck("sync", "Till sync", HealthLevel.WARN,
                    "${i.pendingSync} sales are waiting to reach the main phone. Check both phones are on the same Wi-Fi.", HealthAction.SYNC)
                i.pendingSync > 0 -> HealthCheck("sync", "Till sync", HealthLevel.GOOD,
                    "${i.pendingSync} sale(s) will go across on the next sync.")
                else -> HealthCheck("sync", "Till sync", HealthLevel.GOOD, "Everything has been sent.")
            }
        }

        i.freeStorageMb?.let { mb ->
            out += when {
                mb < 100 -> HealthCheck("storage", "Phone storage", HealthLevel.BAD,
                    "Only $mb MB free. The app may fail to save sales — free up space now.", HealthAction.NONE)
                mb < 500 -> HealthCheck("storage", "Phone storage", HealthLevel.WARN, "$mb MB free. Getting low.")
                else -> HealthCheck("storage", "Phone storage", HealthLevel.GOOD, "$mb MB free.")
            }
        }

        if (i.unclaimedMpesaToday > 0) out += HealthCheck("mpesa", "M-Pesa payments", HealthLevel.WARN,
            "${i.unclaimedMpesaToday} payment(s) received today are not matched to any sale.", HealthAction.MPESA)

        if (i.expired > 0) out += HealthCheck("expired", "Expired stock", HealthLevel.BAD,
            "${i.expired} product(s) are past their expiry date and still in stock.", HealthAction.LOW_STOCK)
        if (i.lowStock > 0) out += HealthCheck("lowstock", "Low stock", HealthLevel.WARN,
            "${i.lowStock} product(s) are running low.", HealthAction.LOW_STOCK)

        if (!i.secureStorageAvailable) out += HealthCheck("secure", "Secure storage", HealthLevel.BAD,
            "This phone could not open its protected key store, so M-Pesa keys and the sync code are not being saved.", HealthAction.SETTINGS)
        if (i.weakPairingCode) out += HealthCheck("pairing", "Sync pairing code", HealthLevel.WARN,
            "This phone still uses an older 6-digit code. Upgrade it to a stronger one in Settings.", HealthAction.SETTINGS)
        if (i.defaultPinStillUsed) out += HealthCheck("pin", "Default PIN", HealthLevel.BAD,
            "An account still uses the factory PIN 1234. Change it.", HealthAction.USERS)
        if (i.staleOpenShifts > 0) out += HealthCheck("shifts", "Open shifts", HealthLevel.WARN,
            "${i.staleOpenShifts} shift(s) have been open for more than 16 hours. Close them to get correct cash totals.", HealthAction.SHIFTS)
        if (i.crashReports > 0) out += HealthCheck("crash", "App crashes", HealthLevel.WARN,
            "The app closed unexpectedly ${i.crashReports} time(s) recently.", HealthAction.NONE)

        return out.sortedByDescending { it.level.ordinal }
    }

    fun overall(checks: List<HealthCheck>): HealthLevel = when {
        checks.any { it.level == HealthLevel.BAD } -> HealthLevel.BAD
        checks.any { it.level == HealthLevel.WARN } -> HealthLevel.WARN
        else -> HealthLevel.GOOD
    }

    fun headline(checks: List<HealthCheck>): String {
        val bad = checks.count { it.level == HealthLevel.BAD }
        val warn = checks.count { it.level == HealthLevel.WARN }
        return when {
            bad > 0 -> "$bad thing(s) need attention now"
            warn > 0 -> "Mostly fine — $warn thing(s) to look at"
            else -> "Your shop's data looks healthy"
        }
    }
}
