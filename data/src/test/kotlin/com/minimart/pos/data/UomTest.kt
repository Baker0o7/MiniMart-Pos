package com.minimart.pos.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.minimart.pos.data.db.AppMigrations
import com.minimart.pos.data.entity.Product
import com.minimart.pos.data.repository.UomError
import com.minimart.pos.data.repository.UomRepository
import com.minimart.pos.data.repository.UomResult
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class UomTest : DbTestBase() {
    private fun repo() = UomRepository(db.uomDao(), db)
    private fun product(unit: String = "pcs", weighed: Boolean = false, name: String = "Soda") = runBlocking {
        val id = db.productDao().insertProduct(Product(barcode = "b$name$unit", name = name, price = 50.0, stock = 100, unit = unit, isWeighed = weighed))
        id
    }
    private fun refused(r: UomResult) = (r as? UomResult.Refused)?.error

    @Test fun defaultsAreAddedOnceAndUnitsInUseAreIncluded() = runBlocking {
        product(unit = "Tray!")   // not a valid code: skipped, never crashes
        product(unit = "bunch", name = "Spinach")
        val r = repo(); r.ensureDefaults(); r.ensureDefaults()
        val codes = r.getUnits().map { it.code }
        assertTrue(codes.containsAll(listOf("pcs", "kg", "carton", "dozen")))
        assertTrue("a unit a product already uses is listed", "bunch" in codes)
        assertEquals("running twice adds nothing", codes.size, codes.toSet().size)
    }

    @Test fun addingUnitsIsCheckedAndCaseInsensitive() = runBlocking {
        val r = repo(); r.ensureDefaults()
        assertEquals(UomResult.Ok, r.addUnit("Sack"))
        assertEquals(UomError.DUPLICATE, refused(r.addUnit("  SACK ")))
        assertEquals(UomError.BAD_NAME, refused(r.addUnit("   ")))
        assertEquals(UomError.BAD_NAME, refused(r.addUnit("a;b")))
    }

    @Test fun renamingACodeMovesProductsAndPacks() = runBlocking {
        val r = repo(); r.ensureDefaults()
        val p = product()
        assertEquals(UomResult.Ok, r.savePack(p, "carton", 24))
        val carton = r.getUnits().first { it.code == "carton" }
        assertEquals(UomResult.Ok, r.edit(carton.id, "Case", "case"))
        assertEquals("case", r.packsFor(p).single().unitCode)
        val pcs = r.getUnits().first { it.code == "pcs" }
        assertEquals(UomResult.Ok, r.edit(pcs.id, "Piece", "piece"))
        assertEquals("piece", db.productDao().getProductById(p)!!.unit)
        assertEquals(UomError.DUPLICATE, refused(r.edit(pcs.id, "Piece", "case")))
    }

    @Test fun aUnitInUseCannotBeDeletedButAFreeOneCan() = runBlocking {
        val r = repo(); r.ensureDefaults()
        val p = product()
        r.savePack(p, "box", 6)
        assertEquals(UomError.IN_USE, refused(r.delete(r.getUnits().first { it.code == "box" }.id)))   // used as a pack
        assertEquals(UomError.IN_USE, refused(r.delete(r.getUnits().first { it.code == "pcs" }.id)))   // used as a base unit
        assertEquals(UomResult.Ok, r.delete(r.getUnits().first { it.code == "tray" }.id))
        assertNull(db.uomDao().getByCode("tray"))
    }

    @Test fun packRulesAreEnforced() = runBlocking {
        val r = repo(); r.ensureDefaults()
        val p = product()
        assertEquals(UomError.BAD_FACTOR, refused(r.savePack(p, "carton", 1)))
        assertEquals(UomError.BAD_FACTOR, refused(r.savePack(p, "carton", 0)))
        assertEquals(UomError.UNKNOWN_UNIT, refused(r.savePack(p, "pallet", 10)))
        assertEquals(UomError.SAME_AS_BASE, refused(r.savePack(p, "pcs", 10)))
        assertEquals(UomError.NO_PRODUCT, refused(r.savePack(9999, "carton", 10)))
        assertEquals(UomError.WEIGHED_PRODUCT, refused(r.savePack(product(unit = "kg", weighed = true, name = "Rice"), "bag", 25)))
        assertEquals(UomResult.Ok, r.savePack(p, "carton", 24))
        assertEquals(UomResult.Ok, r.savePack(p, "carton", 12))   // same unit again changes the size, no duplicate row
        assertEquals(12, r.packsFor(p).single().factor)
    }

    @Test fun deletingAProductRemovesItsPacksAndStockIsUntouched() = runBlocking {
        val r = repo(); r.ensureDefaults()
        val p = product()
        r.savePack(p, "carton", 24)
        assertEquals(100, db.productDao().getProductById(p)!!.stock)   // packs never touch stock
        db.openHelper.writableDatabase.execSQL("DELETE FROM products WHERE id = $p")
        assertTrue(r.packsFor(p).isEmpty())
        assertTrue(r.packsByProduct.first().isEmpty())
    }
}

/** The 21→22 step creates both tables and their unique indexes without touching existing data. */
class Migration21To22Test : DbTestBase() {
    @Test fun createsTheUnitTablesAndKeepsExistingRows() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(21) {
                    override fun onCreate(db: SupportSQLiteDatabase) { db.execSQL("CREATE TABLE products (id INTEGER PRIMARY KEY, unit TEXT NOT NULL)") }
                    override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) {}
                }).build())
        val sdb = helper.writableDatabase
        sdb.execSQL("INSERT INTO products VALUES (1, 'pcs')")
        AppMigrations.MIGRATION_21_22.migrate(sdb)
        AppMigrations.MIGRATION_21_22.migrate(sdb)   // idempotent
        sdb.execSQL("INSERT INTO uoms (code, name, isActive, sortOrder) VALUES ('pcs', 'Pieces', 1, 0)")
        sdb.execSQL("INSERT INTO product_units (productId, unitCode, factor, sellable, purchasable) VALUES (1, 'carton', 24, 1, 1)")
        try { sdb.execSQL("INSERT INTO uoms (code, name, isActive, sortOrder) VALUES ('pcs', 'Again', 1, 1)"); fail("code must be unique") } catch (_: Exception) {}
        try { sdb.execSQL("INSERT INTO product_units (productId, unitCode, factor, sellable, purchasable) VALUES (1, 'carton', 12, 1, 1)"); fail("one pack per unit") } catch (_: Exception) {}
        sdb.query("SELECT unit FROM products WHERE id = 1").use { c -> assertTrue(c.moveToFirst()); assertEquals("pcs", c.getString(0)) }
        helper.close()
    }
}
