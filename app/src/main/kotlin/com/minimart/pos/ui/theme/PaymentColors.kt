package com.minimart.pos.ui.theme

import androidx.compose.ui.graphics.Color
import com.minimart.pos.data.entity.PaymentMethod

/**
 * One colour per way of paying, used by every screen. Why: Reports, Sales History and Analytics each chose
 * their own, so Credit was purple, red and amber on three screens, and two of the choices were too dark to
 * read on the dark background. These are all light enough for text on every accent (checked in PaletteTest),
 * and no payment method uses red, which the app keeps for problems.
 */
fun paymentColor(method: PaymentMethod): Color = when (method) {
    PaymentMethod.CASH   -> Color(0xFF81C784)
    PaymentMethod.MPESA  -> Color(0xFF2DD4BF)
    PaymentMethod.CARD   -> Color(0xFF64B5F6)
    PaymentMethod.MIXED  -> Color(0xFFFFB74D)
    PaymentMethod.CREDIT -> Color(0xFFCE93D8)
}
