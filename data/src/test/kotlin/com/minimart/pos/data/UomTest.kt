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

class UomPackPricingTest : DbTestBase() {
    private fun repo() = UomRepository(db.uomDao(), db)
    private fun product(name: String = "Soda", weighed: Boolean = false) = runBlocking {
        db.productDao().insertProduct(Product(barcode = "b$name", name = name, price = 50.0, stock = 100, unit = "pcs", isWeighed = weighed))
    }
    private fun refused(r: UomResult) = (r as? UomResult.Refused)?.error

    @Test fun packKeepsItsOwnPriceBarcodeAndCost() = runBlocking {
        val r = repo(); r.ensureDefaults(); val id = product()
        assertEquals(UomResult.Ok, r.savePack(id, "carton", 24, price = 1100.0, barcode = "CTN-0001", cost = 900.0))
        val p = db.uomDao().packsFor(id).single()
        assertEquals(1100.0, p.price, 0.0); assertEquals("CTN-0001", p.barcode); assertEquals(900.0, p.cost, 0.0)
        // saving again changes it in place
        assertEquals(UomResult.Ok, r.savePack(id, "carton", 24, price = 0.0, barcode = "CTN-0001", cost = 0.0))
        val again = db.uomDao().packsFor(id).single()
        assertEquals(p.id, again.id); assertEquals(0.0, again.price, 0.0)
    }

    @Test fun aScannedPackBarcodeFindsItsProduct() = runBlocking {
        val r = repo(); r.ensureDefaults(); val id = product()
        r.savePack(id, "carton", 24, barcode = "CTN-0001")
        val hit = r.packForBarcode(" CTN-0001 ")
        assertNotNull(hit); assertEquals(24, hit!!.first.factor); assertEquals(id, hit.second.id)
        assertNull(r.packForBarcode("NOPE-0001")); assertNull(r.packForBarcode(""))
    }

    @Test fun aPackThatIsNotSellableIsNotFoundAtTheTill() = runBlocking {
        val r = repo(); r.ensureDefaults(); val id = product()
        r.savePack(id, "carton", 24, sellable = false, barcode = "CTN-0001")
        assertNull(r.packForBarcode("CTN-0001"))
    }

    @Test fun barcodesAreUniqueAcrossProductsAndPacks() = runBlocking {
        val r = repo(); r.ensureDefaults(); val a = product("A"); val b = product("B")
        assertEquals(UomResult.Ok, r.savePack(a, "carton", 24, barcode = "CTN-0001"))
        assertEquals(UomError.BARCODE_TAKEN, refused(r.savePack(b, "carton", 12, barcode = "CTN-0001")))
        assertEquals(UomError.BARCODE_TAKEN, refused(r.savePack(b, "carton", 12, barcode = "bA")))   // a product's own barcode
        assertEquals(UomResult.Ok, r.savePack(a, "carton", 24, barcode = "CTN-0001"))                // its own barcode again is fine
    }

    @Test fun badBarcodesAndPricesAreRefused() = runBlocking {
        val r = repo(); r.ensureDefaults(); val id = product()
        assertEquals(UomError.BAD_BARCODE, refused(r.savePack(id, "carton", 24, barcode = "12 3")))
        assertEquals(UomError.BAD_PRICE, refused(r.savePack(id, "carton", 24, price = -1.0)))
        assertEquals(UomError.BAD_PRICE, refused(r.savePack(id, "carton", 24, cost = Double.NaN)))
        assertEquals(UomError.BAD_PRICE, refused(r.savePack(id, "carton", 24, price = 1e12)))
        assertTrue(db.uomDao().packsFor(id).isEmpty())
    }

    @Test fun weighedProductsStillTakeNoPacks() = runBlocking {
        val r = repo(); r.ensureDefaults(); val id = product("Rice", weighed = true)
        assertEquals(UomError.WEIGHED_PRODUCT, refused(r.savePack(id, "bag", 50, barcode = "BAG-0001")))
    }
}

class Migration22To23Test : DbTestBase() {
    @Test fun addsThePackColumnsKeepsRowsAndRunsTwice() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val helper = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(ctx).name(null)
                .callback(object : SupportSQLiteOpenHelper.Callback(22) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE product_units (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, productId INTEGER NOT NULL, unitCode TEXT NOT NULL, factor INTEGER NOT NULL, sellable INTEGER NOT NULL, purchasable INTEGER NOT NULL)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, o: Int, n: Int) {}
                }).build())
        val sdb = helper.writableDatabase
        sdb.execSQL("INSERT INTO product_units (productId, unitCode, factor, sellable, purchasable) VALUES (1, 'carton', 24, 1, 1)")
        AppMigrations.MIGRATION_22_23.migrate(sdb)
        AppMigrations.MIGRATION_22_23.migrate(sdb)
        sdb.query("SELECT factor, price, barcode, cost FROM product_units").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(24, c.getInt(0)); assertEquals(0.0, c.getDouble(1), 0.0); assertEquals("", c.getString(2)); assertEquals(0.0, c.getDouble(3), 0.0)
        }
        helper.close()
    }
}
