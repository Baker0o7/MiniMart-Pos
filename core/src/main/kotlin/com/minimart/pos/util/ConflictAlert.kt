package com.minimart.pos.util

/**
 * The words for the alert shown when two tills reversed the same sale in different ways. Pure so the wording is tested
 * in both languages; the notification itself is posted by the app.
 */
object ConflictAlert {
    data class Text(val title: String, val body: String)

    /** [kept] is what this phone already had (and keeps); [other] is what the other till tried to do. */
    fun reversal(receipt: String, kept: String, other: String): Text = Text(
        Lang.t("Two tills disagreed on a sale", "اختلف صندوقان على عملية بيع"),
        Lang.t(
            "Sale $receipt was already ${kept.lowercase()} here, and another till also ${other.lowercase()} it. " +
                "The first one was kept so money is never returned twice. Check which was right.",
            "عملية البيع $receipt كانت ${arState(kept)} هنا، وقام صندوق آخر بـ${arAction(other)} أيضًا. " +
                "احتُفظ بالأول حتى لا يُعاد المال مرتين. تحقق من الصحيح."
        )
    )

    private fun arState(s: String) = when (s.uppercase()) { "REFUNDED" -> "مستردة"; "VOIDED" -> "ملغاة"; else -> s.lowercase() }
    private fun arAction(s: String) = when (s.uppercase()) { "REFUNDED" -> "استردادها"; "VOIDED" -> "إلغائها"; else -> s.lowercase() }
}
