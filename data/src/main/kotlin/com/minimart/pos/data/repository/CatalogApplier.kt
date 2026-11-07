package com.minimart.pos.data.repository

import androidx.room.withTransaction
import com.minimart.pos.data.dao.ProductDao
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.entity.Product
import com.minimart.pos.util.CatalogItem
import javax.inject.Inject
import javax.inject.Singleton

data class CatalogApplyResult(val added: Int, val updated: Int, val deactivated: Int)

/**
 * Moves the shared product list in and out of this shop's product table.
 *
 * Why it is narrow: only the fields an owner wants the same in every shop are touched. Stock, batch, expiry, the
 * low-stock level and the photo belong to this shop and are never read from or written to the shared list, so a
 * pull can never change what is on the shelf or break a sale in progress. Products are matched by barcode.
 */
@Singleton
class CatalogApplier @Inject constructor(private val db: AppDatabase, private val productDao: ProductDao) {

    /** This shop's products in shared form. Deleted products (barcode renamed with "~del") are left out. */
    suspend fun export(): List<CatalogItem> = productDao.getAllIncludingInactive()
        .filter { !(it.barcode.contains("~del")) }
        .map {
            CatalogItem(it.barcode, it.name, it.price, it.costPrice, it.sku, it.category, it.unit, it.taxRate,
                it.supplierName, it.supplierPhone, it.pluCode, it.isWeighed, it.pricePerKg, it.isActive)
        }

    /** [items] must already have passed `CatalogRules.sanitize`. */
    suspend fun apply(items: List<CatalogItem>, now: Long = System.currentTimeMillis()): CatalogApplyResult = db.withTransaction {
        var added = 0; var updated = 0; var deactivated = 0
        for (i in items) {
            val local = productDao.getAnyByBarcode(i.barcode)
            if (local == null) {
                if (!i.isActive) continue
                productDao.insertProduct(Product(
                    barcode = i.barcode, sku = i.sku, name = i.name, price = i.price, costPrice = i.costPrice, stock = 0,
                    category = i.category, unit = i.unit, taxRate = i.taxRate, supplierName = i.supplierName, supplierPhone = i.supplierPhone,
                    pluCode = i.pluCode, isWeighed = i.isWeighed, pricePerKg = i.pricePerKg, isActive = true, createdAt = now, updatedAt = now))
                added++
            } else {
                val merged = local.copy(
                    sku = i.sku, name = i.name, price = i.price, costPrice = i.costPrice, category = i.category, unit = i.unit,
                    taxRate = i.taxRate, supplierName = i.supplierName, supplierPhone = i.supplierPhone, pluCode = i.pluCode,
                    isWeighed = i.isWeighed, pricePerKg = i.pricePerKg, isActive = i.isActive)
                if (merged != local) {
                    productDao.updateProduct(merged.copy(updatedAt = now))
                    if (local.isActive && !i.isActive) deactivated++ else updated++
                }
            }
        }
        CatalogApplyResult(added, updated, deactivated)
    }
}
