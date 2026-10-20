package com.minimart.pos.util

/** Decides whether the first-launch wizard should appear. Shops that already have data never see it. */
object SetupWizard {
    fun shouldShow(wizardDone: Boolean, productCount: Int, saleCount: Int, isOwner: Boolean): Boolean =
        isOwner && !wizardDone && productCount == 0 && saleCount == 0

    /** A store name left at the factory value is worth asking about. */
    fun isDefaultStoreName(name: String) = name.isBlank() || name.trim().equals("My MiniMart", ignoreCase = true)

    fun validStoreName(name: String) = name.trim().length in 2..60
    fun validOpeningPin(pin: String) = pin.length in 4..8 && pin.all { it.isDigit() } && pin != "1234" && pin.toSet().size > 1
}
