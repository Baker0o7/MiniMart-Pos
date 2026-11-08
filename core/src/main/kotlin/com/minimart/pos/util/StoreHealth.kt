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
    val staleOpenShifts: Int = 0,
    /** Distinct handled problems in the last week (see [ProblemLog]). */
    val recentProblems: Int = 0,
    /** Whether the shop database is encrypted on this phone; null when not known. */
    val databaseEncrypted: Boolean? = null,
    /** Hours since this till last synced with the main phone; null when never (see [SyncFreshness]). */
    val lastSyncAgeHours: Long? = null,
    /** Disagreements between phones that were settled automatically and have not been looked at. */
    val syncConflicts: Int = 0,
    /** The app has been closing again and again right after opening (see [CrashLoopGuard]). */
    val crashLoop: Boolean = false
)

/** Turns raw numbers into plain-language checks, worst problems first. Pure, so it can be tested. */
object StoreHealth {
    fun evaluate(i: HealthInputs): List<HealthCheck> {
        val out = mutableListOf<HealthCheck>()

        out += when {
            i.dataProblems == null -> HealthCheck("data", Lang.t("Sales and stock records", "سجلات المبيعات والمخزون"), HealthLevel.WARN,
                Lang.t("Not checked yet. Run the check to make sure totals and stock add up.", "لم يُفحص بعد. شغّل الفحص للتأكد من تطابق الإجماليات والمخزون."), HealthAction.DATA_CHECK)
            i.dataProblems == 0 -> HealthCheck("data", Lang.t("Sales and stock records", "سجلات المبيعات والمخزون"), HealthLevel.GOOD, Lang.t("Everything adds up.", "كل شيء متطابق."))
            else -> HealthCheck("data", Lang.t("Sales and stock records", "سجلات المبيعات والمخزون"), HealthLevel.BAD,
                Lang.t("${i.dataProblems} record(s) do not add up. Open the check to see which.", "${i.dataProblems} سجل غير متطابق. افتح الفحص لمعرفة أيها."), HealthAction.DATA_CHECK)
        }

        out += when {
            !i.backupEverMade -> HealthCheck("backup", Lang.t("Backup", "النسخ الاحتياطي"), HealthLevel.BAD,
                Lang.t("No backup has been made on this phone. If it is lost or broken, your sales history goes with it.", "لم تُنشأ أي نسخة احتياطية على هذا الهاتف. إذا ضاع أو تعطل فسيضيع معه سجل مبيعاتك."), HealthAction.BACKUP)
            i.lastBackupAgeDays == null -> HealthCheck("backup", Lang.t("Backup", "النسخ الاحتياطي"), HealthLevel.WARN,
                Lang.t("A backup exists but its date could not be read.", "توجد نسخة احتياطية لكن تعذّرت قراءة تاريخها."), HealthAction.BACKUP)
            i.lastBackupAgeDays >= 14 -> HealthCheck("backup", Lang.t("Backup", "النسخ الاحتياطي"), HealthLevel.BAD,
                Lang.t("Last backup was ${i.lastBackupAgeDays} days ago.", "آخر نسخة احتياطية كانت منذ ${i.lastBackupAgeDays} يومًا."), HealthAction.BACKUP)
            i.lastBackupAgeDays >= 3 -> HealthCheck("backup", Lang.t("Backup", "النسخ الاحتياطي"), HealthLevel.WARN,
                Lang.t("Last backup was ${i.lastBackupAgeDays} days ago.", "آخر نسخة احتياطية كانت منذ ${i.lastBackupAgeDays} يومًا."), HealthAction.BACKUP)
            else -> HealthCheck("backup", Lang.t("Backup", "النسخ الاحتياطي"), HealthLevel.GOOD,
                if (i.lastBackupAgeDays == 0) Lang.t("Backed up today.", "تم النسخ الاحتياطي اليوم.") else Lang.t("Backed up ${i.lastBackupAgeDays} day(s) ago.", "تم النسخ الاحتياطي منذ ${i.lastBackupAgeDays} يومًا."))
        }

        if (i.syncConfigured || i.pendingSync > 0 || i.refusedSync > 0) {
            out += when {
                i.refusedSync > 0 -> HealthCheck("sync", Lang.t("Till sync", "مزامنة الصندوق"), HealthLevel.BAD,
                    Lang.t("${i.refusedSync} sale(s) were refused by the main phone and exist only on this one.", "رفض الهاتف الرئيسي ${i.refusedSync} عملية بيع وهي موجودة على هذا الهاتف فقط."), HealthAction.SYNC)
                i.pendingSync > 20 -> HealthCheck("sync", Lang.t("Till sync", "مزامنة الصندوق"), HealthLevel.WARN,
                    Lang.t("${i.pendingSync} sales are waiting to reach the main phone. Check both phones are on the same Wi-Fi.", "${i.pendingSync} عملية بيع بانتظار الوصول إلى الهاتف الرئيسي. تأكد أن الهاتفين على نفس شبكة الواي فاي."), HealthAction.SYNC)
                i.pendingSync > 0 -> HealthCheck("sync", Lang.t("Till sync", "مزامنة الصندوق"), HealthLevel.GOOD,
                    Lang.t("${i.pendingSync} sale(s) will go across on the next sync.", "سيتم إرسال ${i.pendingSync} عملية بيع في المزامنة التالية."))
                else -> HealthCheck("sync", Lang.t("Till sync", "مزامنة الصندوق"), HealthLevel.GOOD, Lang.t("Everything has been sent.", "تم إرسال كل شيء."))
            }
        }

        if (i.syncConfigured && i.lastSyncAgeHours != null) {
            val h = i.lastSyncAgeHours
            if (h >= SyncFreshness.STALE_AFTER_HOURS) out += HealthCheck("sync_stale", Lang.t("Data on this phone", "البيانات على هذا الهاتف"), HealthLevel.BAD,
                Lang.t("Last synced ${SyncFreshness.ageText(h * 3_600_000L)} ago. Prices, stock and customer balances here may be out of date.", "آخر مزامنة منذ ${SyncFreshness.ageText(h * 3_600_000L)}. قد تكون الأسعار والمخزون وأرصدة العملاء هنا قديمة."), HealthAction.SYNC)
            else if (h >= SyncFreshness.AGING_AFTER_HOURS) out += HealthCheck("sync_stale", Lang.t("Data on this phone", "البيانات على هذا الهاتف"), HealthLevel.WARN,
                Lang.t("Last synced ${SyncFreshness.ageText(h * 3_600_000L)} ago. Sync when you are near the main phone.", "آخر مزامنة منذ ${SyncFreshness.ageText(h * 3_600_000L)}. زامن عندما تكون قرب الهاتف الرئيسي."), HealthAction.SYNC)
        }
        if (i.syncConflicts > 0) out += HealthCheck("sync_conflicts", Lang.t("Phones disagreed", "اختلاف بين الهواتف"), HealthLevel.WARN,
            Lang.t("${i.syncConflicts} change(s) were edited on two phones at once. The newest edit was kept; check the activity log if something looks wrong.", "تم تعديل ${i.syncConflicts} عنصر على هاتفين في الوقت نفسه. احتُفظ بأحدث تعديل؛ راجع سجل النشاط إذا بدا شيء خاطئًا."), HealthAction.SYNC)
        if (i.crashLoop) out += HealthCheck("crash_loop", Lang.t("App keeps closing", "التطبيق يُغلق باستمرار"), HealthLevel.BAD,
            Lang.t("The app closed several times soon after opening. Background work was held back so you can keep selling. Share the problem report from this screen.", "أُغلق التطبيق عدة مرات بعد فتحه مباشرة. أُوقف العمل في الخلفية لتتمكن من مواصلة البيع. شارك تقرير المشكلة من هذه الشاشة."))

        i.freeStorageMb?.let { mb ->
            out += when {
                mb < 100 -> HealthCheck("storage", Lang.t("Phone storage", "مساحة الهاتف"), HealthLevel.BAD,
                    Lang.t("Only $mb MB free. The app may fail to save sales — free up space now.", "المتاح $mb ميجابايت فقط. قد يفشل التطبيق في حفظ المبيعات، فأفرغ مساحة الآن."), HealthAction.NONE)
                mb < 500 -> HealthCheck("storage", Lang.t("Phone storage", "مساحة الهاتف"), HealthLevel.WARN, Lang.t("$mb MB free. Getting low.", "المتاح $mb ميجابايت. بدأت المساحة تقل."))
                else -> HealthCheck("storage", Lang.t("Phone storage", "مساحة الهاتف"), HealthLevel.GOOD, Lang.t("$mb MB free.", "المتاح $mb ميجابايت."))
            }
        }

        if (i.unclaimedMpesaToday > 0) out += HealthCheck("mpesa", Lang.t("M-Pesa payments", "دفعات M-Pesa"), HealthLevel.WARN,
            Lang.t("${i.unclaimedMpesaToday} payment(s) received today are not matched to any sale.", "${i.unclaimedMpesaToday} دفعة مستلمة اليوم غير مربوطة بأي عملية بيع."), HealthAction.MPESA)

        if (i.expired > 0) out += HealthCheck("expired", Lang.t("Expired stock", "مخزون منتهي الصلاحية"), HealthLevel.BAD,
            Lang.t("${i.expired} product(s) are past their expiry date and still in stock.", "${i.expired} منتج تجاوز تاريخ الصلاحية وما زال في المخزون."), HealthAction.LOW_STOCK)
        if (i.lowStock > 0) out += HealthCheck("lowstock", Lang.t("Low stock", "مخزون منخفض"), HealthLevel.WARN,
            Lang.t("${i.lowStock} product(s) are running low.", "${i.lowStock} منتج على وشك النفاد."), HealthAction.LOW_STOCK)

        if (!i.secureStorageAvailable) out += HealthCheck("secure", Lang.t("Secure storage", "التخزين الآمن"), HealthLevel.BAD,
            Lang.t("This phone could not open its protected key store, so M-Pesa keys and the sync code are not being saved.", "تعذّر على هذا الهاتف فتح مخزن المفاتيح المحمي، لذا لا تُحفظ مفاتيح M-Pesa ورمز المزامنة."), HealthAction.SETTINGS)
        if (i.weakPairingCode) out += HealthCheck("pairing", Lang.t("Sync pairing code", "رمز اقتران المزامنة"), HealthLevel.WARN,
            Lang.t("This phone still uses an older 6-digit code. Upgrade it to a stronger one in Settings.", "ما زال هذا الهاتف يستخدم رمزًا قديمًا من 6 أرقام. رقّه إلى رمز أقوى من الإعدادات."), HealthAction.SETTINGS)
        if (i.defaultPinStillUsed) out += HealthCheck("pin", Lang.t("Default PIN", "الرمز السري الافتراضي"), HealthLevel.BAD,
            Lang.t("An account still uses the factory PIN 1234. Change it.", "ما زال أحد الحسابات يستخدم الرمز السري الافتراضي 1234. غيّره."), HealthAction.USERS)
        if (i.staleOpenShifts > 0) out += HealthCheck("shifts", Lang.t("Open shifts", "ورديات مفتوحة"), HealthLevel.WARN,
            Lang.t("${i.staleOpenShifts} shift(s) have been open for more than 16 hours. Close them to get correct cash totals.", "${i.staleOpenShifts} وردية مفتوحة منذ أكثر من 16 ساعة. أغلقها للحصول على إجماليات نقدية صحيحة."), HealthAction.SHIFTS)
        if (i.crashReports > 0) out += HealthCheck("crash", Lang.t("App crashes", "انهيارات التطبيق"), HealthLevel.WARN,
            Lang.t("The app closed unexpectedly ${i.crashReports} time(s) recently.", "أُغلق التطبيق بشكل غير متوقع ${i.crashReports} مرة مؤخرًا."), HealthAction.NONE)

        if (i.recentProblems > 0) out += HealthCheck("problems", "Problems the app handled", if (i.recentProblems >= 20) HealthLevel.BAD else HealthLevel.WARN,
            Lang.t("${i.recentProblems} different problem(s) in the last week. Open the list below and share it if they keep happening.", "${i.recentProblems} مشكلة مختلفة خلال الأسبوع الماضي. افتح القائمة أدناه وشاركها إذا استمرت."), HealthAction.NONE)
        if (i.databaseEncrypted == false) out += HealthCheck("dbenc", Lang.t("Data protection", "حماية البيانات"), HealthLevel.WARN,
            Lang.t("The shop database is not locked on this phone. Anyone who copies its files could read your sales. Restart the app to lock it; if this stays, share the problem list.", "قاعدة بيانات المتجر غير مقفلة على هذا الهاتف. أي شخص ينسخ ملفاتها يمكنه قراءة مبيعاتك. أعد تشغيل التطبيق لقفلها؛ وإن استمر ذلك فشارك قائمة المشكلات."), HealthAction.NONE)

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
            bad > 0 -> Lang.t("$bad thing(s) need attention now", "$bad أمر يحتاج إلى اهتمام الآن")
            warn > 0 -> Lang.t("Mostly fine — $warn thing(s) to look at", "الوضع جيد عمومًا، مع $warn أمر للمراجعة")
            else -> Lang.t("Your shop's data looks healthy", "بيانات متجرك سليمة")
        }
    }
}
