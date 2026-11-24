package com.minimart.pos.util

import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * The rules behind the M-Pesa prompt (Daraja STK push) that must hold when the network or Safaricom misbehaves, kept pure so
 * they are tested. The one that matters most for money: a push that may already have reached the customer's phone is NEVER
 * sent again automatically, because the customer could then be asked to pay twice.
 */
object DarajaRules {
    enum class HttpKind { OK, AUTH_EXPIRED, RATE_LIMITED, SERVER_BUSY, REJECTED }

    fun httpKind(code: Int): HttpKind = when (code) {
        in 200..299 -> HttpKind.OK
        401, 403 -> HttpKind.AUTH_EXPIRED
        429 -> HttpKind.RATE_LIMITED
        408, 500, 502, 503, 504 -> HttpKind.SERVER_BUSY
        else -> HttpKind.REJECTED
    }

    /** What the app sends when the owner has not set an address. The app confirms payments by asking Safaricom, so it never listens here. */
    const val FALLBACK_CALLBACK = "https://example.com/mpesa/callback"

    /**
     * A usable payment-confirmation address: https only, a real host name (no bare IP, localhost or example domains),
     * no spaces, no login details in the address. Blank is allowed and means "use the fallback".
     */
    fun callbackProblem(url: String): String? {
        val u = url.trim()
        if (u.isEmpty()) return null
        if (u.length > 200 || u.any { it.isWhitespace() }) return "The address is not valid"
        if (!u.startsWith("https://", ignoreCase = true)) return "The address must start with https://"
        val rest = u.substring(8)
        val host = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        if (host.contains('@')) return "The address must not contain a user name or password"
        val name = host.substringBefore(':').lowercase()
        val ipLike = name.all { it.isDigit() || it == '.' }
        val bad = name == "localhost" || name.endsWith(".local") || name == "example.com" || name.endsWith(".example.com") ||
            name.endsWith(".example") || name.endsWith(".invalid") || name.endsWith(".test")
        if (!name.contains('.') || ipLike || bad || name.startsWith(".") || name.endsWith(".")) return "Use the web address of your own server"
        return null
    }

    /** The address to send with a push: the owner's if it is valid, otherwise the fallback. */
    fun callbackFor(saved: String): String = saved.trim().takeIf { it.isNotEmpty() && callbackProblem(it) == null } ?: FALLBACK_CALLBACK

    /** Safaricom's "still being processed" answers to a status query: keep waiting, do not call it a failure. */
    fun isStillProcessing(errorCode: String) = errorCode == "500.001.1001" || errorCode == "500.001.1032"

    /** A failure that happened before the request could have left the phone, so sending the push again cannot double-charge. */
    fun isSafeToRetryPush(t: Throwable): Boolean {
        val chain = generateSequence(t) { it.cause?.takeIf { c -> c !== it } }.take(5).toList()
        return chain.any { neverLeftThePhone(it) } && chain.none { it is SocketTimeoutException && !neverLeftThePhone(it) }
    }

    /** DNS failure, refused or unreachable connection, or a timeout while still connecting: nothing was sent. */
    private fun neverLeftThePhone(t: Throwable) =
        t is UnknownHostException || t is ConnectException || t is NoRouteToHostException ||
            (t is SocketTimeoutException && t.message?.contains("connect", ignoreCase = true) == true)

    /** The request may have been delivered but the answer was lost: the customer might already have the prompt. */
    fun pushOutcomeUnknown(t: Throwable): Boolean {
        val chain = generateSequence(t) { it.cause?.takeIf { c -> c !== it } }.take(5).toList()
        return chain.none { neverLeftThePhone(it) } && chain.any { it is IOException }
    }

    /** Gap before the next status check: steady at first, slower while checks keep failing (max 10 s), so a bad signal is not hammered. */
    fun pollDelayMs(consecutiveErrors: Int): Long = when {
        consecutiveErrors <= 0 -> 3_500L
        consecutiveErrors == 1 -> 5_000L
        consecutiveErrors == 2 -> 7_000L
        else -> 10_000L
    }

    /** Wait before retry number [attempt] (1 = first retry) of a safe request; [jitter] is 0.0..1.0 so tests are exact. */
    fun retryDelayMs(attempt: Int, jitter: Double = 0.0): Long {
        val base = when { attempt <= 1 -> 500L; attempt == 2 -> 1_500L; else -> 3_000L }
        return base + (base * 0.3 * jitter.coerceIn(0.0, 1.0)).toLong()
    }

    const val MAX_ATTEMPTS = 3

    // ── Words shown to the cashier ────────────────────────────────────────────

    fun busyMessage() = Lang.t(
        "M-Pesa is busy or not answering. Wait a minute and try again, or ask the customer for the code in their M-Pesa SMS.",
        "خدمة إم-بيسا مشغولة أو لا تستجيب. انتظر دقيقة ثم حاول مرة أخرى، أو اطلب من العميل الرمز الموجود في رسالة إم-بيسا."
    )

    fun rateLimitedMessage() = Lang.t(
        "Too many M-Pesa requests at once. Wait a few seconds and try again.",
        "طلبات إم-بيسا كثيرة جدًا. انتظر بضع ثوانٍ ثم حاول مرة أخرى."
    )

    fun authMessage() = Lang.t(
        "M-Pesa did not accept the saved keys. Check the consumer key, secret and passkey in Settings, and that Live or Sandbox is right.",
        "لم تقبل إم-بيسا المفاتيح المحفوظة. تحقق من مفتاح المستهلك والسر والرمز في الإعدادات، ومن اختيار الحقيقي أو التجريبي."
    )

    /** The answer was lost after sending: say so plainly and warn against sending twice. */
    fun unknownOutcomeMessage() = Lang.t(
        "No answer from M-Pesa. The customer may already have the prompt, so check their phone before sending again. " +
            "If it arrived, wait for them to pay, or enter the SMS code.",
        "لا رد من إم-بيسا. قد يكون الطلب وصل إلى هاتف العميل، فتحقق منه قبل الإرسال مرة أخرى. " +
            "إذا وصل فانتظر دفعه، أو أدخل الرمز من الرسالة."
    )

    fun sending() = Lang.t("Sending payment request…", "جارٍ إرسال طلب الدفع…")
    fun awaiting() = Lang.t("Prompt sent. Waiting for the customer…", "أُرسل الطلب. بانتظار العميل…")
    fun confirmed() = Lang.t("Payment confirmed ✓", "تم تأكيد الدفع ✓")
    fun timedOut() = Lang.t("No answer yet. Ask the customer for the code in their M-Pesa SMS.", "لا رد بعد. اطلب من العميل الرمز الموجود في رسالة إم-بيسا.")
    fun checkingAgain(reason: String) = Lang.t("Checking again… ($reason)", "جارٍ التحقق مجددًا… ($reason)")

    /** What a failed payment means for the cashier, by Safaricom's result code. [desc] is Safaricom's own text, used only when the code is unknown. */
    fun resultMessage(resultCode: String, desc: String): String = when (resultCode.trim()) {
        "1" -> Lang.t("The customer does not have enough money in M-Pesa.", "لا يوجد رصيد كافٍ في إم-بيسا لدى العميل.")
        "1032" -> Lang.t("The customer cancelled the payment.", "ألغى العميل الدفع.")
        "1037" -> Lang.t("The customer's phone did not respond in time. Ask them to try again.", "لم يستجب هاتف العميل في الوقت المناسب. اطلب منه المحاولة مرة أخرى.")
        "2001" -> Lang.t("The customer entered the wrong M-Pesa PIN.", "أدخل العميل رقم إم-بيسا السري بشكل خاطئ.")
        "1019", "1025", "1001" -> Lang.t("The customer's M-Pesa could not take this payment now. Try again in a moment.", "تعذّر على إم-بيسا لدى العميل قبول هذا الدفع الآن. حاول بعد قليل.")
        else -> desc.filter { it >= ' ' && it != '\u007f' }.trim().take(120)
            .ifBlank { Lang.t("The payment failed (code ${resultCode.trim().take(10)}).", "فشلت عملية الدفع (الرمز ${resultCode.trim().take(10)}).") }
    }
}
