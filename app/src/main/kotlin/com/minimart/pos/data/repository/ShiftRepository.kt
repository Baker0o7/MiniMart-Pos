package com.minimart.pos.data.repository

import com.minimart.pos.data.dao.ShiftDao
import com.minimart.pos.data.entity.PaymentMethod
import com.minimart.pos.data.entity.Shift
import com.minimart.pos.data.entity.ShiftStatus
import kotlinx.coroutines.flow.Flow
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

    suspend fun clockIn(cashierId: Long, cashierName: String, openingFloat: Double): Long {
        val shift = Shift(
            cashierId = cashierId,
            cashierName = cashierName,
            openingFloat = openingFloat,
            status = ShiftStatus.OPEN
        )
        return shiftDao.insertShift(shift)
    }

    suspend fun clockOut(shiftId: Long, closingFloat: Double, notes: String): Shift? {
        val shift = shiftDao.getShiftById(shiftId) ?: return null

        // Aggregate all sales for this shift period
        val sales = saleRepo.getSalesByDateRange(shift.clockIn, System.currentTimeMillis())
        var cashSales = 0.0; var mpesaSales = 0.0; var cardSales = 0.0
        var totalTx = 0; var totalDiscount = 0.0; var totalItems = 0

        // Collect flow once
        val salesList = kotlinx.coroutines.flow.first(
            saleRepo.getSalesByDateRange(shift.clockIn, System.currentTimeMillis())
        ).filter { it.cashierId == shiftId || true } // all sales in window

        salesList.filter { it.status == com.minimart.pos.data.entity.SaleStatus.COMPLETED }
            .forEach { sale ->
                when (sale.paymentMethod) {
                    PaymentMethod.CASH  -> cashSales  += sale.totalAmount
                    PaymentMethod.MPESA -> mpesaSales += sale.totalAmount
                    PaymentMethod.CARD  -> cardSales  += sale.totalAmount
                    else -> {}
                }
                totalTx++
                totalDiscount += sale.discountAmount
            }

        val expected = shift.openingFloat + cashSales
        val discrepancy = closingFloat - expected

        val closed = shift.copy(
            clockOut = System.currentTimeMillis(),
            closingFloat = closingFloat,
            totalCashSales = cashSales,
            totalMpesaSales = mpesaSales,
            totalCardSales = cardSales,
            totalSales = cashSales + mpesaSales + cardSales,
            totalTransactions = totalTx,
            totalDiscounts = totalDiscount,
            expectedCash = expected,
            cashDiscrepancy = discrepancy,
            notes = notes,
            status = ShiftStatus.CLOSED
        )
        shiftDao.updateShift(closed)
        return closed
    }
}
