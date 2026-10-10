package com.minimart.pos.sync

import androidx.room.withTransaction
import com.minimart.pos.data.dao.CustomerDao
import com.minimart.pos.data.dao.ProductDao
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.entity.Customer
import com.minimart.pos.data.entity.Product
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class SnapshotResult(val products: Int, val customers: Int)

/**
 * The main device's catalogue and customer book, sent to tills. Products are matched by barcode and
 * customers by phone, because row ids differ between devices. Stock and credit balances come from
 * the main device, which has already applied every till's sales.
 */
@Singleton
class SnapshotService @Inject constructor(
    private val db: AppDatabase,
    private val productDao: ProductDao,
    private val customerDao: CustomerDao
) {
    suspend fun build(): String {
        val products = JSONArray()
        productDao.getAllIncludingInactive().forEach { p ->
            // Deleted products carry a renamed barcode ("~del"); tills have no use for them.
            if (!p.isActive && p.barcode.contains("~del")) return@forEach
            products.put(JSONObject().apply {
                put("barcode", p.barcode); put("sku", p.sku); put("name", p.name); put("description", p.description)
                put("price", p.price); put("costPrice", p.costPrice); put("stock", p.stock)
                put("lowStockThreshold", p.lowStockThreshold); put("category", p.category); put("unit", p.unit)
                put("taxRate", p.taxRate); put("supplierName", p.supplierName); put("supplierPhone", p.supplierPhone)
                put("reorderQuantity", p.reorderQuantity); put("batchNumber", p.batchNumber); put("expiryDate", p.expiryDate)
                put("pluCode", p.pluCode); put("isWeighed", p.isWeighed); put("pricePerKg", p.pricePerKg)
                put("stockKg", p.stockKg); put("isActive", p.isActive)
                put("createdAt", p.createdAt); put("updatedAt", p.updatedAt)
            })
        }
        val customers = JSONArray()
        customerDao.getAllCustomersList().forEach { c ->
            customers.put(JSONObject().apply {
                put("name", c.name); put("phone", c.phone); put("email", c.email)
                put("creditBalance", c.creditBalance); put("totalPurchases", c.totalPurchases)
                put("visitCount", c.visitCount); put("notes", c.notes); put("createdAt", c.createdAt)
            })
        }
        return JSONObject().put("v", SyncPayloads.VERSION).put("products", products).put("customers", customers).toString()
    }

    /** Applies the main device's snapshot to this device; returns how many rows changed or were added. */
    suspend fun apply(json: String): SnapshotResult = db.withTransaction {
        val root = JSONObject(json)
        require(root.optInt("v", 1) == SyncPayloads.VERSION) { "Unsupported sync version" }
        var productChanges = 0
        val ps = root.getJSONArray("products")
        for (i in 0 until ps.length()) {
            val o = ps.getJSONObject(i)
            val barcode = o.getString("barcode")
            val local = productDao.getAnyByBarcode(barcode)
            val active = o.optBoolean("isActive", true)
            if (local == null) {
                if (!active) continue
                productDao.insertProduct(fromJson(o, Product(barcode = barcode, name = "", price = 0.0, stock = 0)).copy(id = 0))
                productChanges++
            } else {
                val merged = fromJson(o, local)
                if (merged != local) { productDao.updateProduct(merged); productChanges++ }
            }
        }
        var customerChanges = 0
        val cs = root.getJSONArray("customers")
        for (i in 0 until cs.length()) {
            val o = cs.getJSONObject(i)
            val phone = o.optString("phone", "")
            val name = o.getString("name")
            val local = if (phone.isNotBlank()) customerDao.getCustomerByPhone(phone) else customerDao.getCustomerByNameNoPhone(name)
            if (local == null) {
                customerDao.insertCustomer(Customer(
                    name = name, phone = phone, email = o.optString("email", ""),
                    creditBalance = o.optDouble("creditBalance", 0.0), totalPurchases = o.optDouble("totalPurchases", 0.0),
                    visitCount = o.optInt("visitCount", 0), notes = o.optString("notes", ""),
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                ))
                customerChanges++
            } else {
                val merged = local.copy(
                    name = name, email = o.optString("email", local.email),
                    creditBalance = o.optDouble("creditBalance", local.creditBalance),
                    totalPurchases = o.optDouble("totalPurchases", local.totalPurchases),
                    visitCount = o.optInt("visitCount", local.visitCount), notes = o.optString("notes", local.notes)
                )
                if (merged != local) { customerDao.updateCustomer(merged); customerChanges++ }
            }
        }
        SnapshotResult(productChanges, customerChanges)
    }

    /** Remote fields over [base]; the local id, barcode and photo stay as they are on this device. */
    private fun fromJson(o: JSONObject, base: Product) = base.copy(
        sku = o.optString("sku", base.sku), name = o.optString("name", base.name),
        description = o.optString("description", base.description),
        price = o.optDouble("price", base.price), costPrice = o.optDouble("costPrice", base.costPrice),
        stock = o.optInt("stock", base.stock), lowStockThreshold = o.optInt("lowStockThreshold", base.lowStockThreshold),
        category = o.optString("category", base.category), unit = o.optString("unit", base.unit),
        taxRate = o.optDouble("taxRate", base.taxRate),
        supplierName = o.optString("supplierName", base.supplierName), supplierPhone = o.optString("supplierPhone", base.supplierPhone),
        reorderQuantity = o.optInt("reorderQuantity", base.reorderQuantity),
        batchNumber = o.optString("batchNumber", base.batchNumber), expiryDate = o.optLong("expiryDate", base.expiryDate),
        pluCode = o.optString("pluCode", base.pluCode), isWeighed = o.optBoolean("isWeighed", base.isWeighed),
        pricePerKg = o.optDouble("pricePerKg", base.pricePerKg), stockKg = o.optDouble("stockKg", base.stockKg),
        isActive = o.optBoolean("isActive", base.isActive),
        createdAt = o.optLong("createdAt", base.createdAt), updatedAt = o.optLong("updatedAt", base.updatedAt)
    )
}
