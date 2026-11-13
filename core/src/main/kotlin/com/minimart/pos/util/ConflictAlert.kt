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

    /** A till sale this phone could not accept. [reason] is one of the app's own rule messages; it is shortened and stripped of line breaks. */
    fun refusedSale(receipt: String, reason: String): Text {
        val r = reason.filter { it >= ' ' && it != '\u007f' }.replace(Regex("\\s+"), " ").trim().take(120)
        val rec = receipt.filter { it >= ' ' && it != '\u007f' }.trim().take(40).ifBlank { "?" }
        return Text(
            Lang.t("A till sale was refused", "رُفضت عملية بيع من صندوق"),
            Lang.t(
                "Sale $rec from another till was not accepted here" + (if (r.isNotEmpty()) " ($r)" else "") +
                    ". It is still on that till and is not in your totals. Check it and sell it again here if it was real.",
                "لم تُقبل عملية البيع $rec من صندوق آخر هنا" + (if (r.isNotEmpty()) " ($r)" else "") +
                    ". ما زالت على ذلك الصندوق وليست ضمن إجمالياتك. تحقق منها وأعد بيعها هنا إن كانت حقيقية."
            )
        )
    }

    private fun arState(s: String) = when (s.uppercase()) { "REFUNDED" -> "مستردة"; "VOIDED" -> "ملغاة"; else -> s.lowercase() }
    private fun arAction(s: String) = when (s.uppercase()) { "REFUNDED" -> "استردادها"; "VOIDED" -> "إلغائها"; else -> s.lowercase() }
}
