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

        // Collect all sales during this shift window
        val salesList: List<Sale> = saleRepo
            .getSalesByDateRange(shift.clockIn, System.currentTimeMillis())
            .first()

        val completed = salesList.filter { it.status == SaleStatus.COMPLETED }

        var cashSales  = 0.0
        var mpesaSales = 0.0
        var cardSales  = 0.0
        var totalDiscount = 0.0
        val totalTx = completed.size

        completed.forEach { sale ->
            when (sale.paymentMethod) {
                PaymentMethod.CASH  -> cashSales  += sale.totalAmount
                PaymentMethod.MPESA -> mpesaSales += sale.totalAmount
                PaymentMethod.CARD  -> cardSales  += sale.totalAmount
                else -> {}
            }
            totalDiscount += sale.discountAmount
        }

        val totalSales = cashSales + mpesaSales + cardSales
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
