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
    entities = [Product::class, Sale::class, SaleItem::class, User::class, Expense::class, Shift::class],
    version = 4,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun userDao(): UserDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun shiftDao(): ShiftDao
    companion object { const val DATABASE_NAME = "minimart_pos.db" }
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
        val pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"
        db.execSQL("""INSERT INTO users (username, pinHash, displayName, role, isActive, createdAt)
               VALUES ('admin', '$pinHash', 'Owner', 'OWNER', 1, ${System.currentTimeMillis()})""")
        val now = System.currentTimeMillis()
        db.execSQL("""INSERT INTO products (barcode, sku, name, description, price, costPrice, stock, lowStockThreshold, category, unit, taxRate, isActive, createdAt, updatedAt)
            VALUES
            ('6001007519173','DRK001','Coca-Cola 500ml','',50.0,35.0,48,10,'Drinks','pcs',0.16,1,$now,$now),
            ('6009705182370','SNK001','Lays Chips 50g','',30.0,20.0,60,10,'Snacks','pcs',0.16,1,$now,$now),
            ('6001255035069','SNK002','Mentos Roll','',15.0,9.0,100,20,'Snacks','pcs',0.0,1,$now,$now),
            ('6003132024014','PCA001','Vaseline 250ml','',120.0,80.0,25,5,'Personal Care','pcs',0.16,1,$now,$now),
            ('5000159484695','CIG001','Marlboro Red 20s','',350.0,280.0,40,5,'Cigarettes','pcs',0.0,1,$now,$now)""")
    }
}
