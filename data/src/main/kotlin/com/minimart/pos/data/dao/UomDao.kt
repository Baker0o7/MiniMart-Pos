package com.minimart.pos.data.dao

import androidx.room.*
import com.minimart.pos.data.entity.ProductUnit
import com.minimart.pos.data.entity.Uom
import kotlinx.coroutines.flow.Flow

@Dao
interface UomDao {
    @Query("SELECT * FROM uoms ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    fun observeUnits(): Flow<List<Uom>>

    @Query("SELECT * FROM uoms ORDER BY sortOrder ASC, name COLLATE NOCASE ASC")
    suspend fun getUnits(): List<Uom>

    @Query("SELECT * FROM uoms WHERE code = :code LIMIT 1")
    suspend fun getByCode(code: String): Uom?

    @Query("SELECT COUNT(*) FROM uoms")
    suspend fun countUnits(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(unit: Uom): Long

    @Update suspend fun update(unit: Uom)

    @Query("DELETE FROM uoms WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT DISTINCT unit FROM products WHERE unit != ''")
    suspend fun baseUnitsInUse(): List<String>

    /** How many products count in this unit as their base unit. */
    @Query("SELECT COUNT(*) FROM products WHERE unit = :code")
    suspend fun productsUsingAsBase(code: String): Int

    @Query("SELECT COUNT(*) FROM product_units WHERE unitCode = :code")
    suspend fun packsUsing(code: String): Int

    @Query("SELECT * FROM product_units ORDER BY productId, factor")
    fun observeAllPacks(): Flow<List<ProductUnit>>

    @Query("SELECT * FROM product_units WHERE productId = :productId ORDER BY factor")
    suspend fun packsFor(productId: Long): List<ProductUnit>

    /** The sellable pack with this barcode, if any (the till adds the whole pack). */
    @Query("SELECT * FROM product_units WHERE barcode = :barcode AND barcode != '' AND sellable = 1 LIMIT 1")
    suspend fun sellablePackByBarcode(barcode: String): ProductUnit?

    /** Another pack already using this barcode (the one being saved is left out by id). */
    @Query("SELECT COUNT(*) FROM product_units WHERE barcode = :barcode AND barcode != '' AND id != :exceptId")
    suspend fun packBarcodeTaken(barcode: String, exceptId: Long): Int

    /** Packs that have a price of their own, with the product's unit price, to work out carton deals. */
    @Query("""
        SELECT pu.productId AS productId, pu.factor AS factor, pu.price AS packPrice, p.price AS basePrice
        FROM product_units pu JOIN products p ON p.id = pu.productId
        WHERE pu.sellable = 1 AND pu.price > 0 AND p.isWeighed = 0
    """)
    fun observeDealRows(): Flow<List<PackDealRow>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPack(pack: ProductUnit): Long

    @Query("DELETE FROM product_units WHERE productId = :productId AND unitCode = :code")
    suspend fun deletePack(productId: Long, code: String)

    @Query("DELETE FROM product_units WHERE productId = :productId")
    suspend fun deletePacksFor(productId: Long)

    /** Renaming a unit's code keeps every product and pack pointing at it. */
    @Query("UPDATE products SET unit = :new WHERE unit = :old")
    suspend fun renameBaseUnit(old: String, new: String)

    @Query("UPDATE product_units SET unitCode = :new WHERE unitCode = :old")
    suspend fun renamePackUnit(old: String, new: String)
}

data class PackDealRow(val productId: Long, val factor: Int, val packPrice: Double, val basePrice: Double)
