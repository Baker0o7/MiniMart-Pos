package com.minimart.pos.cloud

import com.minimart.pos.util.CatalogItem
import org.json.JSONArray
import org.json.JSONObject

/** Converts the shared product list to and from JSON. Reading is forgiving on purpose: validation is `CatalogRules`. */
object CatalogJson {
    fun toJson(items: List<CatalogItem>): JSONArray = JSONArray().also { arr ->
        items.forEach { i ->
            arr.put(JSONObject().put("barcode", i.barcode).put("name", i.name).put("price", i.price).put("costPrice", i.costPrice)
                .put("sku", i.sku).put("category", i.category).put("unit", i.unit).put("taxRate", i.taxRate)
                .put("supplierName", i.supplierName).put("supplierPhone", i.supplierPhone).put("pluCode", i.pluCode)
                .put("isWeighed", i.isWeighed).put("pricePerKg", i.pricePerKg).put("isActive", i.isActive))
        }
    }

    /** Items that are not objects, or lack a barcode or name, come back as [skipped]; wrong-typed numbers become NaN and are rejected later. */
    data class Parsed(val items: List<CatalogItem>, val skipped: Int)

    fun parse(arr: JSONArray?): Parsed {
        if (arr == null) return Parsed(emptyList(), 0)
        val out = ArrayList<CatalogItem>(minOf(arr.length(), 6000)); var skipped = 0
        for (n in 0 until minOf(arr.length(), 6000)) {
            val o = arr.optJSONObject(n)
            if (o == null || !o.has("barcode") || !o.has("name")) { skipped++; continue }
            out += CatalogItem(
                barcode = o.optString("barcode", ""), name = o.optString("name", ""),
                price = num(o, "price"), costPrice = num(o, "costPrice", 0.0),
                sku = o.optString("sku", ""), category = o.optString("category", ""), unit = o.optString("unit", ""),
                taxRate = num(o, "taxRate", 0.0),
                supplierName = o.optString("supplierName", ""), supplierPhone = o.optString("supplierPhone", ""),
                pluCode = o.optString("pluCode", ""), isWeighed = o.optBoolean("isWeighed", false),
                pricePerKg = num(o, "pricePerKg", 0.0), isActive = o.optBoolean("isActive", true)
            )
        }
        return Parsed(out, skipped + maxOf(0, arr.length() - 6000))
    }

    /** A missing number is [default]; a number of the wrong type is NaN so the rules reject the item instead of guessing. */
    private fun num(o: JSONObject, key: String, default: Double = Double.NaN): Double {
        if (!o.has(key)) return default
        val v = o.opt(key)
        return if (v is Number) v.toDouble() else Double.NaN
    }
}
