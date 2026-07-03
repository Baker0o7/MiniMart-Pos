package com.minimart.pos.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.minimart.pos.data.dao.*
import com.minimart.pos.data.entity.*
import javax.inject.Inject

@Database(
    entities = [Product::class, Sale::class, SaleItem::class, User::class, Expense::class, Shift::class, com.minimart.pos.data.entity.Customer::class, com.minimart.pos.data.entity.CreditTransaction::class, com.minimart.pos.data.entity.SyncLog::class],
    version = 12,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun userDao(): UserDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun shiftDao(): ShiftDao
    abstract fun customerDao(): com.minimart.pos.data.dao.CustomerDao
    abstract fun syncDao(): com.minimart.pos.data.dao.SyncDao
    companion object { const val DATABASE_NAME = "minimart_pos.db" }
}

/**
 * Bug fix: AppDatabase used .fallbackToDestructiveMigration() with NO Migration objects,
 * even though the version number has been bumped repeatedly (most recently v8→v9→v10 in
 * this very project). Every one of those jumps would silently WIPE the entire database —
 * every product, sale, customer, credit balance, and shift record — on any device updating
 * across a version boundary, with no warning.
 *
 * Versions 1–7 were bumped during early, pre-release development (some bumps had no real
 * schema change at all — see the v1→v2 commit, which only fixed a seed-SQL bug and bumped
 * the version purely to force a fresh dev-device DB). Reconstructing exact migrations for
 * that period isn't safe without the original schema JSON (exportSchema was false), so
 * .fallbackToDestructiveMigration() is kept as the fallback for anything below v8.
 *
 * From v8 onward the schema deltas are fully known and verified against the entity diffs,
 * so real migrations are provided. Room tries an explicit Migration first and only falls
 * back to destructive recreation when no explicit path covers the upgrade — so any device
 * currently on v8 or v9 will now upgrade to v10 with all data intact.
 *
 * IMPORTANT for future schema changes: every time the database `version` is bumped, add a
 * matching Migration here (or data will be silently wiped again on the next release).
 */
object AppMigrations {
    /** v8 → v9: weighing-scale / PLU support added to products. */
    val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE products ADD COLUMN pluCode TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE products ADD COLUMN isWeighed INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE products ADD COLUMN pricePerKg REAL NOT NULL DEFAULT 0.0")
        }
    }

    /** v9 → v10: cashPortion added to sales for accurate shift cash reconciliation. */
    val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sales ADD COLUMN cashPortion REAL NOT NULL DEFAULT 0.0")
        }
    }

    /** v10 → v11: added DB indices for performance on high-query columns.
     * SQLite CREATE INDEX is safe on existing tables — no data is affected. */
    val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_createdAt ON sales(createdAt)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sales_status ON sales(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_products_pluCode ON products(pluCode)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_customers_phone ON customers(phone)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_log_status ON sync_log(status)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_log_createdAt ON sync_log(createdAt)")
        }
    }

    /** v11 → v12: weightKg added to sale_items so weighed (PLU/scale) sale records
     * can be correctly reconstructed for receipts, history, and refunds. */
    val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE sale_items ADD COLUMN weightKg REAL NOT NULL DEFAULT 0.0")
        }
    }

    val ALL = arrayOf(MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
}

class AppTypeConverters {
    @TypeConverter fun fromPaymentMethod(v: PaymentMethod): String = v.name
    @TypeConverter fun toPaymentMethod(v: String): PaymentMethod = PaymentMethod.valueOf(v)
    @TypeConverter fun fromSaleStatus(v: SaleStatus): String = v.name
    @TypeConverter fun toSaleStatus(v: String): SaleStatus = SaleStatus.valueOf(v)
    @TypeConverter fun fromUserRole(v: UserRole): String = v.name
    @TypeConverter fun toUserRole(v: String): UserRole = UserRole.valueOf(v)
    @TypeConverter fun fromExpenseCategory(v: ExpenseCategory): String = v.name
    @TypeConverter fun toExpenseCategory(v: String): ExpenseCategory = ExpenseCategory.valueOf(v)
    @TypeConverter fun fromShiftStatus(v: ShiftStatus): String = v.name
    @TypeConverter fun toShiftStatus(v: String): ShiftStatus = ShiftStatus.valueOf(v)
}

class DatabaseCallback @Inject constructor() : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // SHA-256("1234") — will auto-upgrade to Argon2id on first login
        val pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"
        db.execSQL("""INSERT INTO users (username, pinHash, displayName, role, isActive, createdAt)
               VALUES ('admin', '$pinHash', 'Owner', 'OWNER', 1, ${System.currentTimeMillis()})""")
        val now = System.currentTimeMillis()
        db.execSQL("""INSERT INTO products (barcode, sku, name, description, price, costPrice, stock, lowStockThreshold, category, unit, taxRate, supplierName, supplierPhone, reorderQuantity, batchNumber, expiryDate, isActive, createdAt, updatedAt)
            VALUES
            ('6001007519173','DRK001','Coca-Cola 500ml','',50.0,35.0,48,10,'Drinks','pcs',0.16,'','',0,'',0,1,$now,$now),
            ('6009705182370','SNK001','Lays Chips 50g','',30.0,20.0,60,10,'Snacks','pcs',0.16,'','',0,'',0,1,$now,$now),
            ('6001255035069','SNK002','Mentos Roll','',15.0,9.0,100,20,'Snacks','pcs',0.0,'','',0,'',0,1,$now,$now),
            ('6003132024014','PCA001','Vaseline 250ml','',120.0,80.0,25,5,'Personal Care','pcs',0.16,'','',0,'',0,1,$now,$now),
            ('5000159484695','CIG001','Marlboro Red 20s','',350.0,280.0,40,5,'Cigarettes','pcs',0.0,'','',0,'',0,1,$now,$now)""")
    }
}
