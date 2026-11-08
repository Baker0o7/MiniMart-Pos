package com.minimart.pos.util

/**
 * Turns a failure into "what happened, and what to do now" in plain words. Matches on class names, so
 * it works for Android exceptions (SQLite, security) without :core depending on Android.
 * Messages the app wrote itself (IllegalStateException / IllegalArgumentException) are already
 * readable and are passed through.
 */
object ErrorAdvice {
    /**
     * Set once at app start to keep a note of unexpected problems (see [ProblemLog]). Called for every
     * error shown to a person; it must never be able to break the message, so failures in it are ignored.
     */
    @Volatile var onProblem: ((Throwable, String) -> Unit)? = null

    private val doingAr = mapOf(
        "A sync request" to "طلب المزامنة",
        "Adding the credit" to "إضافة الرصيد",
        "Applying a till sale" to "تطبيق عملية بيع من الصندوق",
        "Archiving" to "الأرشفة",
        "Automatic backup" to "النسخ الاحتياطي التلقائي",
        "Backup" to "النسخ الاحتياطي",
        "Checking old sales" to "فحص المبيعات القديمة",
        "Checking the M-Pesa payment" to "التحقق من دفعة M-Pesa",
        "Checkout" to "إتمام البيع",
        "Clock-in" to "تسجيل الدخول للعمل",
        "Clock-out" to "تسجيل الخروج من العمل",
        "Deleting the customer" to "حذف العميل",
        "Fetching M-Pesa payments" to "جلب دفعات M-Pesa",
        "Getting the product list" to "جلب قائمة المنتجات",
        "Importing suppliers" to "استيراد الموردين",
        "Loading the end-of-day list" to "تحميل قائمة نهاية اليوم",
        "Login" to "تسجيل الدخول",
        "Opening the drawer" to "فتح درج النقود",
        "Opening the receipt" to "فتح الإيصال",
        "Printing" to "الطباعة",
        "Reaching the server" to "الاتصال بالخادم",
        "Refund" to "الاسترجاع",
        "Removing the supplier" to "إزالة المورد",
        "Restore" to "الاستعادة",
        "Retrying the refused sales" to "إعادة محاولة المبيعات المرفوضة",
        "Saving the product" to "حفظ المنتج",
        "Saving the supplier" to "حفظ المورد",
        "Scan" to "المسح",
        "Sending to the printer" to "الإرسال إلى الطابعة",
        "Sharing the product list" to "مشاركة قائمة المنتجات",
        "Sync" to "المزامنة",
        "Syncing" to "المزامنة",
        "That" to "هذا الإجراء",
        "The M-Pesa request" to "طلب M-Pesa",
        "The backup" to "النسخ الاحتياطي",
        "The data check" to "فحص البيانات",
        "The health check" to "فحص السلامة",
        "The test" to "الاختبار",
        "The upload" to "الرفع",
        "Turning on instant M-Pesa" to "تفعيل M-Pesa الفوري",
        "Void" to "الإلغاء",
    )

    /** The "what we were doing" label in the chosen language; labels not in the list stay as given. */
    private fun doingText(doing: String) = if (Lang.arabic) doingAr[doing] ?: doing else doing

    fun explain(error: Throwable, doing: String): String {
        if (!ProblemLog.isExpected(error)) try { onProblem?.invoke(error, doing) } catch (_: Throwable) { }
        return Lang.t("$doing didn't work. ${reason(error)}", "${doingText(doing)} لم ينجح. ${reason(error)}")
    }

    /**
     * Like [explain] for a gadget on the other end of a cable or Bluetooth link (a printer, a cash drawer),
     * where "could not read or write the file" would send the owner the wrong way.
     */
    fun explainDevice(error: Throwable, doing: String, device: String): String {
        if (!ProblemLog.isExpected(error)) try { onProblem?.invoke(error, doing) } catch (_: Throwable) { }
        val chain = generateSequence(error) { it.cause?.takeIf { c -> c !== it } }.take(5).toList()
        val link = chain.any { it is java.io.IOException || it::class.java.name.contains("Bluetooth", ignoreCase = true) }
        val permission = chain.any { it is SecurityException }
        return Lang.t("$doing didn't work. ", "${doingText(doing)} لم ينجح. ") + when {
            permission -> reason(error)
            link -> Lang.t("The $device did not respond. Check it is switched on and close to the phone, then try again.", "لم يستجب $device. تأكد من أنه يعمل وقريب من الهاتف ثم حاول مرة أخرى.")
            else -> reason(error)
        }
    }

    fun reason(error: Throwable): String {
        val chain = generateSequence(error) { it.cause?.takeIf { c -> c !== it } }.take(5).toList()
        val names = chain.map { it::class.java.name }
        fun has(part: String) = names.any { it.contains(part, ignoreCase = true) }
        val msg = chain.firstNotNullOfOrNull { it.message?.takeIf { m -> m.isNotBlank() } }.orEmpty()
        val lower = msg.lowercase()

        return when {
            has("SQLiteFull") || "database or disk is full" in lower || "no space left" in lower ->
                Lang.t(
                "The phone's storage is full. Free up some space (delete old photos or videos), then try again.",
                "مساحة الهاتف ممتلئة. أفرغ بعض المساحة (احذف الصور أو الفيديوهات القديمة) ثم حاول مرة أخرى.")
            has("SQLiteDatabaseCorrupt") || "malformed" in lower ->
                Lang.t(
                "The shop database looks damaged. Don't keep selling: restore your latest backup from Settings.",
                "قاعدة بيانات المتجر تبدو تالفة. لا تواصل البيع: استعد أحدث نسخة احتياطية من الإعدادات.")
            has("SQLiteDiskIO") ->
                Lang.t(
                "The phone could not write to its storage. Restart the phone and try again; if it keeps happening, make a backup.",
                "تعذّر على الهاتف الكتابة في وحدة التخزين. أعد تشغيل الهاتف وحاول مرة أخرى؛ وإن تكرر ذلك فأنشئ نسخة احتياطية.")
            has("SQLiteConstraint") || "unique constraint" in lower ->
                Lang.t(
                "That already exists (for example a barcode or code that is already used). Check it and try again.",
                "هذا موجود بالفعل (مثل باركود أو رمز مستخدم من قبل). راجعه وحاول مرة أخرى.")
            has("SQLiteDatabaseLock") || "database is locked" in lower ->
                Lang.t(
                "The app was busy with something else. Wait a few seconds and try again.",
                "كان التطبيق مشغولًا بشيء آخر. انتظر بضع ثوانٍ وحاول مرة أخرى.")
            has("WrongPassphrase") ->
                Lang.t(
                "Wrong passphrase, or the file is damaged. Check the passphrase and try again.",
                "عبارة المرور خاطئة أو الملف تالف. تحقق من عبارة المرور وحاول مرة أخرى.")
            has("UnknownHost") || has("ConnectException") || has("NoRouteToHost") ->
                Lang.t(
                "Could not reach the other device or the internet. Check the Wi-Fi or mobile data and try again.",
                "تعذّر الوصول إلى الجهاز الآخر أو الإنترنت. تحقق من الواي فاي أو بيانات الجوال وحاول مرة أخرى.")
            has("SocketTimeout") || has("TimeoutException") ->
                Lang.t(
                "It took too long to answer. Check the connection and try again.",
                "استغرق الرد وقتًا طويلًا. تحقق من الاتصال وحاول مرة أخرى.")
            has("javax.net.ssl") || has("CertificateException") ->
                Lang.t(
                "A secure connection could not be set up. Check the phone's date and time are correct, then try again.",
                "تعذّر إنشاء اتصال آمن. تأكد من صحة تاريخ الهاتف ووقته ثم حاول مرة أخرى.")
            has("SecurityException") ->
                Lang.t(
                "The app doesn't have permission for this. Allow it in the phone's Settings > Apps > MiniMart POS > Permissions.",
                "ليس لدى التطبيق إذن لهذا. اسمح به من إعدادات الهاتف ‏> التطبيقات ‏> MiniMart POS ‏> الأذونات.")
            has("FileNotFound") ->
                Lang.t(
                "A file the app needed was not found. It may have been moved or deleted.",
                "لم يُعثر على ملف يحتاجه التطبيق. ربما نُقل أو حُذف.")
            has("OutOfMemory") ->
                Lang.t(
                "The phone ran out of memory. Close other apps and try again.",
                "نفدت ذاكرة الهاتف. أغلق التطبيقات الأخرى وحاول مرة أخرى.")
            has("IOException") ->
                Lang.t(
                "Could not read or write the file. Check there is free space and try again.",
                "تعذّرت قراءة الملف أو الكتابة فيه. تأكد من وجود مساحة فارغة وحاول مرة أخرى.")
            has("CancellationException") -> Lang.t("It was cancelled.", "تم الإلغاء.")
            (error is IllegalStateException || error is IllegalArgumentException) && msg.isNotBlank() -> msg.trimEnd('.') + "."
            else -> Lang.t(
                "Something unexpected went wrong. Try again; if it repeats, restart the app and share the crash note when it offers.",
                "حدث خطأ غير متوقع. حاول مرة أخرى؛ وإن تكرر فأعد تشغيل التطبيق وشارك ملاحظة الانهيار عند عرضها.")
        }
    }
}
