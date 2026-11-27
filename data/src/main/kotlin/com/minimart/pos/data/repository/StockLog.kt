package com.minimart.pos.data.repository

import com.minimart.pos.data.db.AppDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Names the reason for stock changes made inside [block], so the stock history says "sale", "delivery" or "loss" and not
 * just "changed". It only labels: the history lines are written by database triggers, so a change made without
 * a label is still recorded (as OTHER). Used inside a transaction, the label is undone with it.
 */
suspend fun <T> AppDatabase.tagStock(reason: String, ref: Long = 0, block: suspend () -> T): T {
    val dao = stockMovementDao()
    dao.setContext(reason, ref)
    try {
        return block()
    } finally {
        runCatching { dao.clearContext() }
    }
}

/** The same label for code that has no database handle (view models). */
@Singleton
class StockLog @Inject constructor(private val db: AppDatabase) {
    suspend fun <T> tag(reason: String, ref: Long = 0, block: suspend () -> T): T = db.tagStock(reason, ref, block)
}
