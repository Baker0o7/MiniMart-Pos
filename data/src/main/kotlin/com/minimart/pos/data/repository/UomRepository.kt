package com.minimart.pos.data.repository

import androidx.room.withTransaction
import com.minimart.pos.data.dao.UomDao
import com.minimart.pos.data.db.AppDatabase
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.entity.ProductUnit
import com.minimart.pos.data.entity.Uom
import com.minimart.pos.util.Money
import com.minimart.pos.util.UnitMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Why a unit change was refused. The screens turn these into plain words (English and Arabic). */
enum class UomError { BAD_NAME, DUPLICATE, IN_USE, BAD_FACTOR, UNKNOWN_UNIT, SAME_AS_BASE, NO_PRODUCT, WEIGHED_PRODUCT, NOT_FOUND, BAD_BARCODE, BARCODE_TAKEN, BAD_PRICE }

sealed class UomResult {
    object Ok : UomResult()
    data class Refused(val error: UomError) : UomResult()
}

/**
 * Units of measure. Keeps the managed unit list and each product's pack sizes. Everything that changes stock still counts
 * the product's base unit, so none of this can change what a sale, refund or batch does.
 */
@Singleton
class UomRepository @Inject constructor(private val dao: UomDao, private val db: AppDatabase) {

    val units: Flow<List<Uom>> = dao.observeUnits()

    /** Every product's pack sizes, by product id. */
    val packsByProduct: Flow<Map<Long, List<ProductUnit>>> = dao.observeAllPacks().map { all -> all.groupBy { it.productId } }

    /**
     * Adds the standard units the first time, and any unit a product already uses (shops typed "bottle" or "tray" before
     * this list existed) so nothing in use is missing from it. Safe to run on every start.
     */
    suspend fun ensureDefaults() = db.withTransaction {
        if (dao.countUnits() == 0) DEFAULTS.forEachIndexed { i, (code, name) -> dao.insertIgnore(Uom(code = code, name = name, sortOrder = i)) }
        val known = dao.getUnits().map { it.code }.toMutableSet()
        dao.baseUnitsInUse().forEach { raw ->
            val code = UnitMath.normalizeCode(raw) ?: return@forEach
            if (known.add(code)) dao.insertIgnore(Uom(code = code, name = code.replaceFirstChar { it.uppercase() }, sortOrder = known.size))
        }
    }

    suspend fun getUnits(): List<Uom> = dao.getUnits()

    suspend fun addUnit(name: String, code: String? = null): UomResult {
        val cleanName = name.trim()
        val c = UnitMath.normalizeCode(code ?: cleanName)
        if (cleanName.isEmpty() || cleanName.length > 30 || c == null) return UomResult.Refused(UomError.BAD_NAME)
        if (dao.getByCode(c) != null) return UomResult.Refused(UomError.DUPLICATE)
        dao.insertIgnore(Uom(code = c, name = cleanName, sortOrder = dao.countUnits()))
        return UomResult.Ok
    }

    /** Changes a unit's name and code; products and packs that used the old code follow it. */
    suspend fun edit(id: Long, name: String, code: String): UomResult = db.withTransaction {
        val current = dao.getUnits().firstOrNull { it.id == id } ?: return@withTransaction UomResult.Refused(UomError.NOT_FOUND)
        val cleanName = name.trim()
        val c = UnitMath.normalizeCode(code)
        if (cleanName.isEmpty() || cleanName.length > 30 || c == null) return@withTransaction UomResult.Refused(UomError.BAD_NAME)
        if (c != current.code && dao.getByCode(c) != null) return@withTransaction UomResult.Refused(UomError.DUPLICATE)
        if (c != current.code) { dao.renameBaseUnit(current.code, c); dao.renamePackUnit(current.code, c) }
        dao.update(current.copy(name = cleanName, code = c))
        UomResult.Ok
    }

    suspend fun setActive(id: Long, active: Boolean): UomResult {
        val current = dao.getUnits().firstOrNull { it.id == id } ?: return UomResult.Refused(UomError.NOT_FOUND)
        dao.update(current.copy(isActive = active))
        return UomResult.Ok
    }

    /** A unit that any product uses (as its base unit or a pack) cannot be deleted; hide it instead. */
    suspend fun delete(id: Long): UomResult = db.withTransaction {
        val current = dao.getUnits().firstOrNull { it.id == id } ?: return@withTransaction UomResult.Refused(UomError.NOT_FOUND)
        if (dao.productsUsingAsBase(current.code) > 0 || dao.packsUsing(current.code) > 0) return@withTransaction UomResult.Refused(UomError.IN_USE)
        dao.delete(id)
        UomResult.Ok
    }

    suspend fun packsFor(productId: Long): List<ProductUnit> = dao.packsFor(productId)

    /** Adds or changes one pack size of a product ("carton = 24"). Weighed products are counted in kg and take no packs. */
    suspend fun savePack(
        productId: Long, code: String, factor: Int, sellable: Boolean = true, purchasable: Boolean = true,
        price: Double = 0.0, barcode: String = "", cost: Double = 0.0
    ): UomResult = db.withTransaction {
        val product = db.productDao().getProductById(productId) ?: return@withTransaction UomResult.Refused(UomError.NO_PRODUCT)
        if (product.isWeighed) return@withTransaction UomResult.Refused(UomError.WEIGHED_PRODUCT)
        if (!UnitMath.validFactor(factor)) return@withTransaction UomResult.Refused(UomError.BAD_FACTOR)
        val c = UnitMath.normalizeCode(code) ?: return@withTransaction UomResult.Refused(UomError.BAD_NAME)
        if (dao.getByCode(c) == null) return@withTransaction UomResult.Refused(UomError.UNKNOWN_UNIT)
        if (c == UnitMath.normalizeCode(product.unit)) return@withTransaction UomResult.Refused(UomError.SAME_AS_BASE)
        if (price.isNaN() || cost.isNaN() || price < 0.0 || cost < 0.0 || price > MAX_MONEY || cost > MAX_MONEY) return@withTransaction UomResult.Refused(UomError.BAD_PRICE)
        val bc = UnitMath.normalizeBarcode(barcode) ?: return@withTransaction UomResult.Refused(UomError.BAD_BARCODE)
        val existing = dao.packsFor(productId).firstOrNull { it.unitCode == c }
        if (bc.isNotEmpty() && (db.productDao().getAnyByBarcode(bc) != null || dao.packBarcodeTaken(bc, existing?.id ?: 0L) > 0))
            return@withTransaction UomResult.Refused(UomError.BARCODE_TAKEN)
        dao.upsertPack(ProductUnit(
            id = existing?.id ?: 0, productId = productId, unitCode = c, factor = factor, sellable = sellable, purchasable = purchasable,
            price = Money.fromDouble(price).toDouble(), barcode = bc, cost = Money.fromDouble(cost).toDouble()
        ))
        UomResult.Ok
    }

    /** Carton deals by product, kept up to date as prices and packs change. Only packs with a price below the single-unit total count. */
    fun observePackDeals(): Flow<Map<Long, List<com.minimart.pos.util.PackDeal>>> = dao.observeDealRows().map { rows ->
        rows.mapNotNull { r -> UnitMath.deal(r.basePrice, r.factor, r.packPrice)?.let { r.productId to it } }
            .groupBy({ it.first }, { it.second })
    }

    /** The sellable pack a scanned barcode belongs to, with its product, or null when it is not a pack barcode. */
    suspend fun packForBarcode(barcode: String): Pair<ProductUnit, Product>? {
        val code = barcode.trim()
        if (code.isEmpty()) return null
        val pack = dao.sellablePackByBarcode(code) ?: return null
        val product = db.productDao().getProductById(pack.productId)?.takeIf { it.isActive && !it.isWeighed } ?: return null
        return pack to product
    }

    suspend fun removePack(productId: Long, code: String) = dao.deletePack(productId, code)

    companion object {
        /** Prices and costs above this are almost certainly typing mistakes. */
        const val MAX_MONEY = 10_000_000.0

        /** The units a new shop starts with, in the order they are listed. */
        val DEFAULTS: List<Pair<String, String>> = listOf(
            "pcs" to "Pieces", "kg" to "Kilogram", "g" to "Gram", "litre" to "Litre", "ml" to "Millilitre", "dozen" to "Dozen",
            "pack" to "Pack", "carton" to "Carton", "box" to "Box", "bag" to "Bag", "bottle" to "Bottle", "crate" to "Crate", "tray" to "Tray"
        )
    }
}
