package com.minimart.pos.data.db

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database triggers that write a `stock_movements` line whenever a product's stock changes, by any route (a sale, a
 * delivery, a count, an edit in the product screen, a sync). They are created each time the database opens, so a
 * restored backup or a sync cannot leave the app without them.
 *
 * The reason comes from the one-row `stock_context` table, which the code sets inside its own transaction before it
 * changes stock. No reason means OTHER, which is what makes unexplained changes visible.
 */
object StockTriggers {
    private const val NOW = "CAST(strftime('%s','now') AS INTEGER) * 1000"
    private const val REASON = "COALESCE((SELECT reason FROM stock_context WHERE id = 1 AND reason != ''), 'OTHER')"
    private const val REF = "COALESCE((SELECT ref FROM stock_context WHERE id = 1), 0)"

    val STATEMENTS = listOf(
        """CREATE TRIGGER IF NOT EXISTS trg_stock_units AFTER UPDATE OF stock ON products
           WHEN NEW.isWeighed = 0 AND NEW.stock != OLD.stock
           BEGIN
             INSERT INTO stock_movements (productId, at, delta, reason, ref, stockAfter)
             VALUES (NEW.id, $NOW, NEW.stock - OLD.stock, $REASON, $REF, NEW.stock);
           END""",
        """CREATE TRIGGER IF NOT EXISTS trg_stock_kg AFTER UPDATE OF stockKg ON products
           WHEN NEW.isWeighed = 1 AND NEW.stockKg != OLD.stockKg
           BEGIN
             INSERT INTO stock_movements (productId, at, delta, reason, ref, stockAfter)
             VALUES (NEW.id, $NOW, NEW.stockKg - OLD.stockKg, $REASON, $REF, NEW.stockKg);
           END""",
        """CREATE TRIGGER IF NOT EXISTS trg_stock_new AFTER INSERT ON products
           WHEN (NEW.isWeighed = 0 AND NEW.stock != 0) OR (NEW.isWeighed = 1 AND NEW.stockKg != 0)
           BEGIN
             INSERT INTO stock_movements (productId, at, delta, reason, ref, stockAfter)
             VALUES (NEW.id, $NOW,
                     CASE WHEN NEW.isWeighed = 1 THEN NEW.stockKg ELSE NEW.stock END,
                     COALESCE((SELECT reason FROM stock_context WHERE id = 1 AND reason != ''), 'NEW_PRODUCT'), $REF,
                     CASE WHEN NEW.isWeighed = 1 THEN NEW.stockKg ELSE NEW.stock END);
           END"""
    )

    fun install(db: SupportSQLiteDatabase) {
        STATEMENTS.forEach { db.execSQL(it) }
        db.execSQL("INSERT OR IGNORE INTO stock_context (id, reason, ref) VALUES (1, '', 0)")
    }
}
