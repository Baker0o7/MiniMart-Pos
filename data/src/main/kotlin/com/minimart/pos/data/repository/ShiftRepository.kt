package com.minimart.pos.data.repository

import com.minimart.pos.util.CashUp
import com.minimart.pos.util.Money
import com.minimart.pos.util.sum
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

/** Everything printed on the end-of-shift Z-report. [zNumber] is derived from the shift id. */
data class ZReport(
    val shift: Shift,
    val zNumber: String,
    val voidCount: Int,
    val voidTotal: Double,
    val refundCount: Int,
    val refundTotal: Double,
    val creditSales: Double,
    val noSaleOpens: Int
)

@Singleton
class ShiftRepository @Inject constructor(
    private val shiftDao: ShiftDao,
    private val saleRepo: SaleRepository,
    private val settings: SettingsRepository
) {
    fun getAllShifts(): Flow<List<Shift>> = shiftDao.getAllShifts()
    fun getShiftsForCashier(id: Long): Flow<List<Shift>> = shiftDao.getShiftsForCashier(id)
    fun getRecentShifts(startMs: Long): Flow<List<Shift>> = shiftDao.getRecentShifts(startMs)
    suspend fun getOpenShift(cashierId: Long): Shift? = shiftDao.getOpenShift(cashierId)
    suspend fun openShifts(): List<Shift> = shiftDao.getAllOpen()

    // Bug fix: no validation existed anywhere on openingFloat — a negative value would
    // corrupt expectedCash = openingFloat + cashSales in ShiftRepository.clockOut(),
    // silently masking a real till shortage or fabricating a false discrepancy. Clamped
    // here as defense-in-depth (the UI-level fix in ShiftScreen is the primary guard)
    // so this can't regress even if a future caller bypasses that screen.
    suspend fun clockIn(cashierId: Long, cashierName: String, openingFloat: Double): Long {
        // A cashier can only have one open shift: a double-tap used to create two, and
        // getOpenShift() would then return an arbitrary one while the other never closed.
        shiftDao.getOpenShift(cashierId)?.let { return it.id }
        return shiftDao.insertShift(
            Shift(
                cashierId = cashierId,
                cashierName = cashierName,
                openingFloat = openingFloat.coerceAtLeast(0.0),
                status = ShiftStatus.OPEN
            )
        )
    }

    suspend fun clockOut(shiftId: Long, closingFloat: Double, notes: String): Shift? {
        val shift = shiftDao.getShiftById(shiftId) ?: return null
        // Bug fix: same defense-in-depth as clockIn's openingFloat above — a negative
        // closingFloat would corrupt discrepancy = closingFloat - expectedCash, the
        // core shift-reconciliation number shown to the owner.
        val safeClosingFloat = closingFloat.coerceAtLeast(0.0)

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

        // Every total is added as exact cents (Money), never as Doubles, so the drawer figure cannot drift.
        fun m(v: Double) = Money.fromDouble(v)
        var cashSales  = Money.ZERO
        var mpesaSales = Money.ZERO
        var cardSales  = Money.ZERO
        var totalDiscount = Money.ZERO
        val totalTx = completed.size

        completed.forEach { sale ->
            // Exhaustive `when` (no `else`): the compiler flags a new PaymentMethod that this
            // reconciliation does not handle. Only the cash share of a split sale is in the drawer.
            when (sale.paymentMethod) {
                PaymentMethod.CASH, PaymentMethod.MIXED ->
                    cashSales += CashUp.cashFromSale(sale.paymentMethod.name, m(sale.totalAmount), m(sale.cashPortion))
                PaymentMethod.MPESA  -> mpesaSales += m(sale.totalAmount)
                PaymentMethod.CARD   -> cardSales  += m(sale.totalAmount)
                PaymentMethod.CREDIT -> { /* no cash collected: buy on account */ }
            }
            totalDiscount += m(sale.discountAmount)
        }

        // totalSales is every completed sale whatever the payment method; expectedCash is cash only.
        val totalSales = completed.map { m(it.totalAmount) }.sum()
        val count = CashUp.reconcile(m(shift.openingFloat) + cashSales, m(safeClosingFloat))
        val expectedCash = count.expected.toDouble()
        val discrepancy = count.variance.toDouble()

        val closed = shift.copy(
            clockOut        = System.currentTimeMillis(),
            closingFloat    = safeClosingFloat,
            totalCashSales  = cashSales.toDouble(),
            totalMpesaSales = mpesaSales.toDouble(),
            totalCardSales  = cardSales.toDouble(),
            totalSales      = totalSales.toDouble(),
            totalTransactions = totalTx,
            totalDiscounts  = totalDiscount.toDouble(),
            expectedCash    = expectedCash,
            cashDiscrepancy = discrepancy,
            notes           = notes,
            status          = ShiftStatus.CLOSED
        )
        shiftDao.updateShift(closed)
        return closed
    }

    /** Builds the Z-report for a shift (works for open shifts too, as an X-report). */
    suspend fun zReport(shiftId: Long): ZReport? {
        val shift = shiftDao.getShiftById(shiftId) ?: return null
        val end = shift.clockOut ?: System.currentTimeMillis()
        val mine = saleRepo.getSalesByDateRange(shift.clockIn, end).first()
            .filter { it.cashierId == shift.cashierId }
        val voided = mine.filter { it.status == SaleStatus.VOIDED }
        val refunded = mine.filter { it.status == SaleStatus.REFUNDED }
        val credit = mine.filter { it.status == SaleStatus.COMPLETED }.sumOf {
            when (it.paymentMethod) {
                PaymentMethod.CREDIT -> it.totalAmount
                PaymentMethod.MIXED -> (it.totalAmount - it.cashPortion).coerceAtLeast(0.0)
                else -> 0.0
            }
        }
        return ZReport(
            shift = shift,
            zNumber = "Z-" + shift.id.toString().padStart(4, '0'),
            voidCount = voided.size, voidTotal = voided.sumOf { it.totalAmount },
            refundCount = refunded.size, refundTotal = refunded.sumOf { it.totalAmount },
            creditSales = credit,
            noSaleOpens = settings.noSaleOpens(shift.id)
        )
    }
}
