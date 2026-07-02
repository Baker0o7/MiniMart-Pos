package com.minimart.pos.data.repository

import com.minimart.pos.data.dao.ShiftDao
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Sale
import com.minimart.pos.data.entity.SaleStatus
import com.minimart.pos.data.entity.Shift
import com.minimart.pos.data.entity.ShiftStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShiftRepository @Inject constructor(
    private val shiftDao: ShiftDao,
    private val saleRepo: SaleRepository
) {
    fun getAllShifts(): Flow<List<Shift>> = shiftDao.getAllShifts()
    fun getShiftsForCashier(id: Long): Flow<List<Shift>> = shiftDao.getShiftsForCashier(id)
    fun getRecentShifts(startMs: Long): Flow<List<Shift>> = shiftDao.getRecentShifts(startMs)
    suspend fun getOpenShift(cashierId: Long): Shift? = shiftDao.getOpenShift(cashierId)

    suspend fun clockIn(cashierId: Long, cashierName: String, openingFloat: Double): Long =
        shiftDao.insertShift(
            Shift(
                cashierId = cashierId,
                cashierName = cashierName,
                openingFloat = openingFloat,
                status = ShiftStatus.OPEN
            )
        )

    suspend fun clockOut(shiftId: Long, closingFloat: Double, notes: String): Shift? {
        val shift = shiftDao.getShiftById(shiftId) ?: return null

        // Bug fix: no guard against closing an already-COMPLETED shift. Clocking out
        // a closed shift a second time produced a different (wrong) summary because
        // totalSales was recalculated against the current timestamp, potentially
        // including sales from a later shift in the window.
        if (shift.clockOut != null) return shift

        // Bug fix: was getSalesByDateRange (all statuses) filtered in Kotlin.
        // Use getCompletedSalesByDateRange to filter in SQL and avoid loading
        // VOIDED/REFUNDED sales into memory.
        val completed: List<Sale> = saleRepo
            .getCompletedSalesByDateRange(shift.clockIn, System.currentTimeMillis())
            .first()
            .filter { it.cashierId == shift.cashierId }

        var cashSales  = 0.0
        var mpesaSales = 0.0
        var cardSales  = 0.0
        var totalDiscount = 0.0
        val totalTx = completed.size

        completed.forEach { sale ->
            // Bug fix: this `when` used to be `CASH/MPESA/CARD -> ...; else -> {}`, which
            // silently dropped MIXED (split cash+credit) sales from cashSales entirely.
            // The cash portion of a split payment is real money in the till — omitting it
            // made expectedCash understated, showing a false "shortage" at shift close for
            // any cashier who processed split payments. Now exhaustive (no `else` branch)
            // so the compiler itself will flag it if a new PaymentMethod is ever added
            // without updating this reconciliation logic.
            when (sale.paymentMethod) {
                PaymentMethod.CASH   -> cashSales  += sale.totalAmount
                PaymentMethod.MPESA  -> mpesaSales += sale.totalAmount
                PaymentMethod.CARD   -> cardSales  += sale.totalAmount
                PaymentMethod.MIXED  -> cashSales  += sale.cashPortion // only the cash actually collected
                PaymentMethod.CREDIT -> { /* no cash collected — buy on account */ }
            }
            totalDiscount += sale.discountAmount
        }

        // Bug fix: was cashSales + mpesaSales + cardSales, which excludes MIXED and
        // CREDIT sales entirely — understating total shift revenue for any cashier who
        // processed a split payment or buy-on-account sale. totalSales should reflect
        // every completed sale's value regardless of how it was paid; expectedCash (the
        // till count) is the one that correctly stays cash-method-specific.
        val totalSales = completed.sumOf { it.totalAmount }
        val expectedCash = shift.openingFloat + cashSales
        val discrepancy = closingFloat - expectedCash

        val closed = shift.copy(
            clockOut        = System.currentTimeMillis(),
            closingFloat    = closingFloat,
            totalCashSales  = cashSales,
            totalMpesaSales = mpesaSales,
            totalCardSales  = cardSales,
            totalSales      = totalSales,
            totalTransactions = totalTx,
            totalDiscounts  = totalDiscount,
            expectedCash    = expectedCash,
            cashDiscrepancy = discrepancy,
            notes           = notes,
            status          = ShiftStatus.CLOSED
        )
        shiftDao.updateShift(closed)
        return closed
    }
}
