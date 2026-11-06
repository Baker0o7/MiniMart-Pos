package com.minimart.pos.ui.theme

import androidx.compose.ui.graphics.Color
import com.minimart.pos.data.entity.PaymentMethod

/**
 * One colour per way of paying, used by every screen. Why: Reports, Sales History and Analytics each chose
 * their own, so Credit was purple, red and amber on three screens, and two of the choices were too dark to
 * read on the dark background. These are all light enough for text on every accent (checked in PaletteTest),
 * and no payment method uses red, which the app keeps for problems.
 */
fun paymentColor(method: PaymentMethod, light: Boolean = DT.light): Color = if (light) when (method) {
    PaymentMethod.CASH   -> Color(0xFF1B5E20)
    PaymentMethod.MPESA  -> Color(0xFF00695C)
    PaymentMethod.CARD   -> Color(0xFF0D47A1)
    PaymentMethod.MIXED  -> Color(0xFF8A5300)
    PaymentMethod.CREDIT -> Color(0xFF7B1FA2)
} else when (method) {
    PaymentMethod.CASH   -> Color(0xFF81C784)
    PaymentMethod.MPESA  -> Color(0xFF2DD4BF)
    PaymentMethod.CARD   -> Color(0xFF64B5F6)
    PaymentMethod.MIXED  -> Color(0xFFFFB74D)
    PaymentMethod.CREDIT -> Color(0xFFCE93D8)
}
