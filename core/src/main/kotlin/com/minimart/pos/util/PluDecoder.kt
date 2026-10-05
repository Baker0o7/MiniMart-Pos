package com.minimart.pos.util

/**
 * PLU (Price Look-Up) barcode decoder for weighing scales.
 *
 * Standard format used by most retail scales (EAN-13 variable weight):
 *   2 P P P P P W W W W W C
 *   │ └──────┘ └──────┘ └─ Check digit
 *   │   PLU      Weight
 *   └─ Prefix: 2x = variable weight product
 *
 * Example: "2000010005001"
 *   Prefix = 2, PLU = 00001, Weight = 00050 (50g), Check = 1
 *   (substring(1,6)="00001" is the PLU; substring(6,11)="00050" is the weight —
 *   verify by counting characters: 2-00001-00050-1, 13 digits total)
 *
 * Some scales use different sub-formats:
 *   21xxxxx = price-embedded (not weight)
 *   22-29   = vendor-specific
 *
 * We support: prefix 20-29 with 5-digit PLU + 5-digit weight in grams.
 */
object PluDecoder {

    data class PluResult(
        val pluCode: String,      // 5-digit PLU code
        val weightGrams: Double,  // Weight in grams
        val weightKg: Double      // Weight in kg (convenience)
    )

    /**
     * Returns PluResult if barcode is a variable-weight EAN-13,
     * null if it's a regular fixed-price barcode.
     */
    fun decode(barcode: String): PluResult? {
        val clean = barcode.trim()
        // Must be 13 digits starting with 2
        if (clean.length != 13) return null
        if (!clean.startsWith("2"))   return null
        if (!clean.all { it.isDigit() }) return null

        return try {
            val plu          = clean.substring(1, 6)   // digits 2-6
            val weightStr    = clean.substring(6, 11)  // digits 7-11
            val weightGrams  = weightStr.toDouble()
            PluResult(
                pluCode      = plu,
                weightGrams  = weightGrams,
                weightKg     = weightGrams / 1000.0
            )
        } catch (_: Exception) { null }
    }

    /**
     * Check if a barcode looks like a PLU/weight barcode
     * (starts with 2 + 12 more digits).
     */
    fun isPluBarcode(barcode: String): Boolean =
        barcode.length == 13 && barcode.startsWith("2") && barcode.all { it.isDigit() }

    /**
     * Calculate the line price from a PLU scan.
     * @param pricePerKg  Product's selling price per kg
     * @param weightKg    Weight from the scale barcode
     */
    fun calculatePrice(pricePerKg: Double, weightKg: Double): Double =
        (pricePerKg * weightKg * 100).toLong() / 100.0  // round to 2dp
}
