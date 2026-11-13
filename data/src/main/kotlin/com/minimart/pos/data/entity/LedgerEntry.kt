package com.minimart.pos.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One line of the shadow ledger: a second, separate record of money coming in and being taken back, in whole cents.
 *
 * Every sale writes a SALE line when it is saved, and a refund or void writes a REVERSAL line for the same amount with
 * the opposite sign, in the same transaction. Nothing ever updates or deletes a line (the app has no code that can).
 * The cents are worked out from the sale's decimal amount, independently of the `totalCents` column the reports add,
 * so if a bug, a bad sync or a damaged file ever changes one record without the other, `IntegrityChecker` sees the two
 * disagree and Store health says so. Lines outlive archived sales on purpose.
 */
@Entity(tableName = "ledger_entries", indices = [Index("saleId")])
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** When the line was written (ms). */
    val at: Long = System.currentTimeMillis(),
    /** [SALE] or [REVERSAL]. */
    val kind: String,
    val saleId: Long,
    val receipt: String,
    /** Plus for a sale, minus for a reversal. */
    val cents: Long,
    /** The cash share of the same amount, same sign. */
    val cashCents: Long,
    val method: String
) {
    companion object { const val SALE = "SALE"; const val REVERSAL = "REVERSAL" }
}
