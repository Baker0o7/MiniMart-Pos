package com.minimart.pos.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.minimart.pos.data.dao.ProductDao
import com.minimart.pos.data.dao.SaleDao
import com.minimart.pos.data.dao.UserDao
import com.minimart.pos.data.entity.*

@Database(
    entities = [Product::class, Sale::class, SaleItem::class, User::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(AppTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun userDao(): UserDao

    companion object {
        const val DATABASE_NAME = "minimart_pos.db"
    }
}

class AppTypeConverters {
    @TypeConverter fun fromPaymentMethod(value: PaymentMethod): String = value.name
    @TypeConverter fun toPaymentMethod(value: String): PaymentMethod = PaymentMethod.valueOf(value)

    @TypeConverter fun fromSaleStatus(value: SaleStatus): String = value.name
    @TypeConverter fun toSaleStatus(value: String): SaleStatus = SaleStatus.valueOf(value)

    @TypeConverter fun fromUserRole(value: UserRole): String = value.name
    @TypeConverter fun toUserRole(value: String): UserRole = UserRole.valueOf(value)
}

/** Called once when the DB is first created — seeds a default admin user. */
class DatabaseCallback : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        // Insert default owner account (PIN: 1234 → SHA-256 hash)
        val pinHash = "03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4"
        db.execSQL(
            """INSERT INTO users (username, pinHash, displayName, role, isActive, createdAt)
               VALUES ('admin', '$pinHash', 'Owner', 'OWNER', 1, ${System.currentTimeMillis()})"""
        )
        // Seed sample categories/products for demo
        val now = System.currentTimeMillis()
        db.execSQL("""
            INSERT INTO products (barcode, name, price, costPrice, stock, lowStockThreshold, category, unit, taxRate, isActive, createdAt, updatedAt)
            VALUES 
            ('6001007519173', 'Coca-Cola 500ml', 50.0, 35.0, 48, 10, 'Drinks', 'pcs', 0.16, 1, $now, $now),
            ('6009705182370', 'Lays Chips 50g', 30.0, 20.0, 60, 10, 'Snacks', 'pcs', 0.16, 1, $now, $now),
            ('6001255035069', 'Mentos Roll', 15.0, 9.0, 100, 20, 'Snacks', 'pcs', 0.0, 1, $now, $now),
            ('6003132024014', 'Vaseline 250ml', 120.0, 80.0, 25, 5, 'Personal Care', 'pcs', 0.16, 1, $now, $now),
            ('5000159484695', 'Marlboro Red 20s', 350.0, 280.0, 40, 5, 'Cigarettes', 'pcs', 0.0, 1, $now, $now)
        """)
    }
}
