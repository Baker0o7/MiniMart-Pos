package com.minimart.pos.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CashUpTest {
    private fun m(v: Double) = Money.fromDouble(v)

    @Test fun onlyTheCashShareOfAMixedSaleIsCounted() {
        assertEquals(m(300.0), CashUp.cashFromSale("CASH", m(300.0), m(0.0)))
        assertEquals(m(120.0), CashUp.cashFromSale("MIXED", m(300.0), m(120.0)))
        assertEquals(Money.ZERO, CashUp.cashFromSale("MPESA", m(300.0), m(300.0)))
        assertEquals(Money.ZERO, CashUp.cashFromSale("CREDIT", m(300.0), m(0.0)))
    }

    @Test fun aCashShareLargerThanTheBillIsCappedAtTheBill() {
        assertEquals(m(300.0), CashUp.cashFromSale("MIXED", m(300.0), m(500.0)))
    }

    @Test fun differenceUnderOneShillingIsBalanced() {
        assertEquals(CashVerdict.BALANCED, CashUp.reconcile(m(1000.0), m(1000.5)).verdict)
        assertEquals(CashVerdict.BALANCED, CashUp.reconcile(m(1000.0), m(999.5)).verdict)
    }

    @Test fun shortAndOverAreNamedWithTheExactDifference() {
        val short = CashUp.reconcile(m(1000.0), m(950.0))
        assertEquals(CashVerdict.SHORT, short.verdict); assertEquals(m(-50.0), short.variance)
        val over = CashUp.reconcile(m(1000.0), m(1010.0))
        assertEquals(CashVerdict.OVER, over.verdict); assertEquals(m(10.0), over.variance)
    }

    @Test fun tenTenCentSalesAddUpToExactlyOneShilling() {
        val total = (1..10).map { m(0.1) }.sum()
        assertEquals(m(1.0), total)
    }

    @Test fun theDayAddsClosedShiftsAndListsOpenOnes() {
        val d = CashUp.day(listOf(
            ShiftCash("Amina", m(1500.0), m(1490.0)),
            ShiftCash("Brian", m(2000.0), m(2020.0)),
            ShiftCash("Cate", m(800.0), null)
        ))
        assertEquals(m(3500.0), d.expected); assertEquals(m(3510.0), d.counted)
        assertEquals(m(10.0), d.variance); assertEquals(CashVerdict.OVER, d.verdict)
        assertEquals(2, d.closedShifts); assertEquals(listOf("Cate"), d.openShifts)
    }

    @Test fun anEmptyDayIsBalanced() {
        val d = CashUp.day(emptyList())
        assertEquals(CashVerdict.BALANCED, d.verdict); assertEquals(0, d.closedShifts)
    }
}

class MoneySumsTest {
    @Test fun tenTenCentAmountsSumToExactlyOneShilling() {
        assertEquals(1.0, List(10) { 0.1 }.sumOfMoney { it }, 0.0)
        assertEquals(0.3, addMoney(0.1, 0.2), 0.0)
    }

    @Test fun manySmallSalesNeverDriftFromTheCentTotal() {
        val rnd = java.util.Random(42)
        val cents = List(5_000) { rnd.nextInt(100_000) }           // sale totals, in cents
        val asDoubles = cents.map { it / 100.0 }
        assertEquals(cents.sumOf { it.toLong() }, Math.round(asDoubles.sumOfMoney { it } * 100))
    }

    @Test fun anEmptyListIsZero() {
        assertEquals(0.0, emptyList<Double>().sumOfMoney { it }, 0.0)
    }
}
