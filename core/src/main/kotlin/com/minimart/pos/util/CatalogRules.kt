package com.minimart.pos.util

/**
 * One product as shared between shops: the things an owner wants the same everywhere (name, price, tax, supplier),
 * and nothing that belongs to a single shop (stock, batch, expiry, low-stock level, photo).
 */
data class CatalogItem(
    val barcode: String, val name: String, val price: Double, val costPrice: Double = 0.0,
    val sku: String = "", val category: String = "General", val unit: String = "pcs", val taxRate: Double = 0.0,
    val supplierName: String = "", val supplierPhone: String = "",
    val pluCode: String = "", val isWeighed: Boolean = false, val pricePerKg: Double = 0.0, val isActive: Boolean = true
)

/**
 * Checks a shared product list that arrived over the internet before it can touch the database. Anything from the
 * network is untrusted: a damaged or tampered list must not be able to set a negative price, a huge number, or a
 * thousand-character name. Items that fail are dropped one by one; the rest still apply.
 */
object CatalogRules {
    const val MAX_ITEMS = 5000
    const val MAX_PRICE = 100_000_000.0

    private val BARCODE = Regex("^[A-Za-z0-9._\\-]{1,40}$")

    data class Result(val items: List<CatalogItem>, val rejected: Int)

    fun sanitize(raw: List<CatalogItem>): Result {
        val out = LinkedHashMap<String, CatalogItem>()
        var rejected = 0
        for (r in raw.take(MAX_ITEMS)) {
            val c = clean(r)
            if (c == null) rejected++ else out[c.barcode] = c   // a repeated barcode keeps the last one
        }
        rejected += (raw.size - raw.take(MAX_ITEMS).size)
        return Result(out.values.toList(), rejected)
    }

    internal fun clean(i: CatalogItem): CatalogItem? {
        val barcode = i.barcode.trim()
        if (!BARCODE.matches(barcode)) return null
        val name = text(i.name, 120)
        if (name.isEmpty()) return null
        if (!money(i.price) || !money(i.costPrice) || !money(i.pricePerKg)) return null
        if (!i.taxRate.isFinite() || i.taxRate < 0.0 || i.taxRate > 100.0) return null
        return i.copy(
            barcode = barcode, name = name, sku = text(i.sku, 40),
            category = text(i.category, 60).ifEmpty { "General" }, unit = text(i.unit, 20).ifEmpty { "pcs" },
            supplierName = text(i.supplierName, 80), supplierPhone = text(i.supplierPhone, 30), pluCode = text(i.pluCode, 20)
        )
    }

    private fun money(v: Double) = v.isFinite() && v >= 0.0 && v <= MAX_PRICE

    /** Single line, no control characters, trimmed and cut to [max]. */
    private fun text(s: String, max: Int): String =
        s.filter { !it.isISOControl() || it == ' ' }.trim().take(max)

    /** A cheap fingerprint of a list, so a shop only publishes when something actually changed. */
    fun fingerprint(items: List<CatalogItem>): String {
        var h = 1125899906842597L
        for (i in items.sortedBy { it.barcode }) {
            for (c in "${i.barcode}|${i.name}|${i.price}|${i.costPrice}|${i.sku}|${i.category}|${i.unit}|${i.taxRate}|${i.supplierName}|${i.supplierPhone}|${i.pluCode}|${i.isWeighed}|${i.pricePerKg}|${i.isActive};") h = 31 * h + c.code
        }
        return java.lang.Long.toHexString(h) + "-" + items.size
    }
}
