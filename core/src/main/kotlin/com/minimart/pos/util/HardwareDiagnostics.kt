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
    /** Print attempts that failed in the last 24 hours, and hours since one last worked (null = never recorded). */
    val printerFailuresToday: Int = 0,
    val printerLastOkHoursAgo: Long? = null,
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
            !i.bluetoothSupported -> HealthCheck("bluetooth", Lang.t("Bluetooth", "البلوتوث"), HealthLevel.WARN,
                Lang.t("This device has no Bluetooth, so a Bluetooth printer or scanner cannot be used. A USB or keyboard-style scanner still works.", "لا يحتوي هذا الجهاز على بلوتوث، فلا يمكن استخدام طابعة أو ماسح بلوتوث. ما زال ماسح USB أو الماسح بنمط لوحة المفاتيح يعمل."))
            !i.bluetoothPermission -> HealthCheck("bluetooth", Lang.t("Bluetooth", "البلوتوث"), HealthLevel.BAD,
                Lang.t("The app is not allowed to use Bluetooth. Allow \"Nearby devices\" in the phone's app settings.", "التطبيق غير مسموح له باستخدام البلوتوث. اسمح بـ \"الأجهزة القريبة\" في إعدادات التطبيق بالهاتف."))
            i.bluetoothEnabled == false -> HealthCheck("bluetooth", Lang.t("Bluetooth", "البلوتوث"), HealthLevel.BAD,
                Lang.t("Bluetooth is switched off. Turn it on to reach the printer.", "البلوتوث مغلق. شغّله للوصول إلى الطابعة."))
            i.bluetoothEnabled == null -> HealthCheck("bluetooth", Lang.t("Bluetooth", "البلوتوث"), HealthLevel.WARN, Lang.t("Bluetooth state could not be read.", "تعذّرت قراءة حالة البلوتوث."))
            else -> HealthCheck("bluetooth", Lang.t("Bluetooth", "البلوتوث"), HealthLevel.GOOD, Lang.t("On and allowed.", "يعمل ومسموح به."))
        }

        out += printer(i)
        out += drawer(i)

        out += when {
            !i.cameraPresent -> HealthCheck("camera", Lang.t("Camera scanner", "ماسح الكاميرا"), HealthLevel.WARN,
                Lang.t("No camera found. Use a Bluetooth or keyboard-style scanner, or type barcodes in.", "لم يتم العثور على كاميرا. استخدم ماسح بلوتوث أو ماسحًا بنمط لوحة المفاتيح، أو اكتب الباركود يدويًا."))
            !i.cameraPermission -> HealthCheck("camera", Lang.t("Camera scanner", "ماسح الكاميرا"), HealthLevel.BAD,
                Lang.t("The app is not allowed to use the camera, so it cannot scan barcodes. Allow Camera in the phone's app settings.", "التطبيق غير مسموح له باستخدام الكاميرا، فلا يمكنه مسح الباركود. اسمح بالكاميرا في إعدادات التطبيق بالهاتف."))
            else -> HealthCheck("camera", Lang.t("Camera scanner", "ماسح الكاميرا"), HealthLevel.GOOD,
                if (i.flashPresent) Lang.t("Camera allowed. Flash available for dark shops.", "الكاميرا مسموح بها. الفلاش متاح للمتاجر المعتمة.") else Lang.t("Camera allowed. No flash on this device.", "الكاميرا مسموح بها. لا يوجد فلاش في هذا الجهاز."))
        }

        out += when {
            !i.smsTrackingOn -> HealthCheck("sms", Lang.t("M-Pesa messages", "رسائل M-Pesa"), HealthLevel.GOOD, Lang.t("Reading M-Pesa texts is off. Turn it on in Settings to log payments automatically.", "قراءة رسائل M-Pesa متوقفة. شغّلها من الإعدادات لتسجيل الدفعات تلقائيًا."))
            !i.smsPermission -> HealthCheck("sms", Lang.t("M-Pesa messages", "رسائل M-Pesa"), HealthLevel.BAD,
                Lang.t("Tracking is on but the app is not allowed to read texts, so payments will not be logged. Allow SMS in the phone's app settings.", "التتبع يعمل لكن التطبيق غير مسموح له بقراءة الرسائل، لذا لن تُسجَّل الدفعات. اسمح بالرسائل النصية في إعدادات التطبيق بالهاتف."))
            else -> HealthCheck("sms", Lang.t("M-Pesa messages", "رسائل M-Pesa"), HealthLevel.GOOD, Lang.t("Allowed. New M-Pesa texts are logged as they arrive.", "مسموح. تُسجَّل رسائل M-Pesa الجديدة فور وصولها."))
        }

        out += if (i.notificationsAllowed) HealthCheck("notifications", Lang.t("Alerts", "التنبيهات"), HealthLevel.GOOD, Lang.t("Low-stock and expiry alerts can be shown.", "يمكن عرض تنبيهات المخزون المنخفض وانتهاء الصلاحية."))
        else HealthCheck("notifications", Lang.t("Alerts", "التنبيهات"), HealthLevel.WARN,
            Lang.t("Notifications are blocked, so low-stock and expiry alerts will not appear. Allow them in the phone's app settings.", "الإشعارات محظورة، لذا لن تظهر تنبيهات المخزون المنخفض وانتهاء الصلاحية. اسمح بها في إعدادات التطبيق بالهاتف."))

        out += when {
            i.networkConnected == null -> HealthCheck("network", Lang.t("Network", "الشبكة"), HealthLevel.WARN, Lang.t("Network state could not be read.", "تعذّرت قراءة حالة الشبكة."))
            !i.networkConnected -> HealthCheck("network", Lang.t("Network", "الشبكة"), HealthLevel.WARN,
                Lang.t("No connection. Selling works offline; syncing, the cloud dashboard and M-Pesa prompts need a connection.", "لا يوجد اتصال. البيع يعمل دون إنترنت؛ أما المزامنة ولوحة السحابة وطلبات M-Pesa فتحتاج إلى اتصال."))
            i.onWifi == false -> HealthCheck("network", Lang.t("Network", "الشبكة"), HealthLevel.GOOD,
                Lang.t("Connected by mobile data. Syncing between phones needs both on the same Wi-Fi.", "متصل ببيانات الجوال. تحتاج المزامنة بين الهواتف إلى أن يكون الهاتفان على نفس شبكة الواي فاي."))
            else -> HealthCheck("network", Lang.t("Network", "الشبكة"), HealthLevel.GOOD, Lang.t("Connected by Wi-Fi.", "متصل بالواي فاي."))
        }

        out += when {
            i.freeStorageMb == null -> HealthCheck("storage", Lang.t("Phone storage", "مساحة الهاتف"), HealthLevel.WARN, Lang.t("Free space could not be read.", "تعذّرت قراءة المساحة الفارغة."))
            i.freeStorageMb < 100 -> HealthCheck("storage", Lang.t("Phone storage", "مساحة الهاتف"), HealthLevel.BAD,
                Lang.t("Only ${i.freeStorageMb} MB free. Sales may fail to save. Free some space.", "المتاح ${i.freeStorageMb} ميجابايت فقط. قد يفشل حفظ المبيعات. أفرغ بعض المساحة."))
            i.freeStorageMb < 500 -> HealthCheck("storage", Lang.t("Phone storage", "مساحة الهاتف"), HealthLevel.WARN, Lang.t("${i.freeStorageMb} MB free. Getting low.", "المتاح ${i.freeStorageMb} ميجابايت. بدأت المساحة تقل."))
            else -> HealthCheck("storage", Lang.t("Phone storage", "مساحة الهاتف"), HealthLevel.GOOD, Lang.t("${i.freeStorageMb} MB free.", "المتاح ${i.freeStorageMb} ميجابايت."))
        }

        out += when {
            i.batteryPercent == null -> HealthCheck("battery", Lang.t("Battery", "البطارية"), HealthLevel.GOOD, Lang.t("Battery level not available.", "مستوى البطارية غير متاح."))
            i.charging -> HealthCheck("battery", Lang.t("Battery", "البطارية"), HealthLevel.GOOD, Lang.t("${i.batteryPercent}% and charging.", "${i.batteryPercent}% وتجري الشحن."))
            i.batteryPercent < 15 -> HealthCheck("battery", Lang.t("Battery", "البطارية"), HealthLevel.BAD, Lang.t("${i.batteryPercent}%. Plug in before you stop being able to sell.", "${i.batteryPercent}%. صِل الشاحن قبل أن تعجز عن البيع."))
            i.batteryPercent < 30 -> HealthCheck("battery", Lang.t("Battery", "البطارية"), HealthLevel.WARN, Lang.t("${i.batteryPercent}%. Keep a charger close.", "${i.batteryPercent}%. أبقِ الشاحن قريبًا."))
            else -> HealthCheck("battery", Lang.t("Battery", "البطارية"), HealthLevel.GOOD, Lang.t("${i.batteryPercent}%.", "${i.batteryPercent}%."))
        }

        out += when {
            i.fontScale >= 1.5f -> HealthCheck("display", Lang.t("Screen text size", "حجم نص الشاشة"), HealthLevel.WARN,
                Lang.t("Text is set very large (${percent(i.fontScale)}). The app adapts, but some screens may need scrolling.", "حجم النص كبير جدًا (${percent(i.fontScale)}). يتكيف التطبيق، لكن بعض الشاشات قد تحتاج إلى تمرير."))
            else -> HealthCheck("display", Lang.t("Screen text size", "حجم نص الشاشة"), HealthLevel.GOOD,
                Lang.t("Text at ${percent(i.fontScale)} on a ${i.screenWidthDp} dp wide screen.", "النص بنسبة ${percent(i.fontScale)} على شاشة عرضها ${i.screenWidthDp} dp."))
        }

        return out.sortedBy { it.level.ordinal.let { o -> 2 - o } }   // BAD first, then WARN, then GOOD (stable)
    }

    private fun printer(i: HardwareInputs): HealthCheck = when {
        !i.printerChosen -> HealthCheck("printer", Lang.t("Receipt printer", "طابعة الإيصالات"), HealthLevel.WARN,
            Lang.t("No printer chosen. Pick one in Settings to print receipts. Digital receipts still work.", "لم تُختر طابعة. اختر واحدة من الإعدادات لطباعة الإيصالات. الإيصالات الرقمية ما زالت تعمل."))
        i.printerPaired == false -> HealthCheck("printer", Lang.t("Receipt printer", "طابعة الإيصالات"), HealthLevel.BAD,
            Lang.t("${name(i.printerName)} is no longer paired with this phone. Pair it again in the phone's Bluetooth settings.", "${name(i.printerName)} لم تعد مقترنة بهذا الهاتف. اقرنها مجددًا من إعدادات البلوتوث في الهاتف."))
        i.printerTest is TestOutcome.Failed -> HealthCheck("printer", Lang.t("Receipt printer", "طابعة الإيصالات"), HealthLevel.BAD,
            Lang.t("Test print failed: ${i.printerTest.reason}", "فشلت الطباعة التجريبية: ${i.printerTest.reason}"))
        i.printerTest == TestOutcome.Passed -> HealthCheck("printer", Lang.t("Receipt printer", "طابعة الإيصالات"), HealthLevel.GOOD,
            Lang.t("${name(i.printerName)} printed the test page.", "${name(i.printerName)} طبعت الصفحة التجريبية."))
        i.printerFailuresToday >= 2 -> HealthCheck("printer", Lang.t("Receipt printer", "طابعة الإيصالات"),
            if (i.printerFailuresToday >= 5) HealthLevel.BAD else HealthLevel.WARN,
            Lang.t(
                "Printing failed ${i.printerFailuresToday} times in the last day" +
                    (i.printerLastOkHoursAgo?.let { ", and last worked ${if (it < 1) "less than an hour" else "$it hour(s)"} ago" } ?: "") +
                    ". Check the paper, the cover and the power. Until it is fixed, receipts can still be shared as a PDF or on WhatsApp.",
                "فشلت الطباعة ${i.printerFailuresToday} مرات خلال اليوم الأخير" +
                    (i.printerLastOkHoursAgo?.let { "، وآخر مرة عملت فيها كانت منذ ${if (it < 1) "أقل من ساعة" else "$it ساعة"}" } ?: "") +
                    ". تحقق من الورق والغطاء والطاقة. إلى أن تُصلح، يمكن مشاركة الإيصالات بصيغة PDF أو عبر واتساب."))
        else -> HealthCheck("printer", Lang.t("Receipt printer", "طابعة الإيصالات"), HealthLevel.GOOD,
            Lang.t("${name(i.printerName)} is paired. Tap Test print to be sure it works.", "${name(i.printerName)} مقترنة. اضغط طباعة تجريبية للتأكد من عملها."))
    }

    private fun drawer(i: HardwareInputs): HealthCheck = when {
        i.drawerTest is TestOutcome.Failed -> HealthCheck("drawer", Lang.t("Cash drawer", "درج النقود"), HealthLevel.BAD,
            Lang.t("The drawer did not open: ${i.drawerTest.reason}", "لم يُفتح الدرج: ${i.drawerTest.reason}"))
        i.drawerTest == TestOutcome.Passed -> HealthCheck("drawer", Lang.t("Cash drawer", "درج النقود"), HealthLevel.GOOD,
            Lang.t("The open signal was sent. If the drawer stayed shut, try the other pin in Settings.", "أُرسلت إشارة الفتح. إذا بقي الدرج مغلقًا فجرّب الدبوس الآخر من الإعدادات."))
        i.drawerConfigured || i.printerChosen -> HealthCheck("drawer", Lang.t("Cash drawer", "درج النقود"), HealthLevel.GOOD,
            Lang.t("Tap Test drawer to send the open signal.", "اضغط اختبار الدرج لإرسال إشارة الفتح."))
        else -> HealthCheck("drawer", Lang.t("Cash drawer", "درج النقود"), HealthLevel.GOOD, Lang.t("No printer or drawer set up, so there is nothing to open.", "لم تُعدّ طابعة أو درج، فلا يوجد ما يُفتح."))
    }

    private fun name(n: String?) = n?.takeIf { it.isNotBlank() } ?: Lang.t("The printer", "الطابعة")
    private fun percent(scale: Float) = "${(scale * 100).toInt()}%"

    /** Worst level in a list; GOOD when empty. */
    fun overall(checks: List<HealthCheck>): HealthLevel = StoreHealth.overall(checks)

    fun headline(checks: List<HealthCheck>): String {
        val bad = checks.count { it.level == HealthLevel.BAD }
        val warn = checks.count { it.level == HealthLevel.WARN }
        return when {
            bad > 0 -> Lang.t("$bad thing(s) to fix", "$bad أمر يجب إصلاحه")
            warn > 0 -> Lang.t("Working, with $warn thing(s) to look at", "يعمل، مع $warn أمر للمراجعة")
            else -> Lang.t("Everything checks out", "كل شيء سليم")
        }
    }
}
