package com.minimart.pos.util

import com.minimart.pos.util.ConflictMerge.Field
import com.minimart.pos.util.ConflictMerge.ReversalOutcome
import com.minimart.pos.util.ConflictMerge.Stamp
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ConflictMergeTest {
    private fun f(v: String, at: Long, dev: String = "A") = Field(v, Stamp(at, dev))

    @Test fun `newest edit wins for a plain field and the loser is reported`() {
        val m = ConflictMerge.merge(mapOf("price" to f("120", 10)), mapOf("price" to f("130", 20, "B")))
        assertEquals("130", m.fields.getValue("price").value)
        assertEquals(1, m.conflicts.size)
        assertEquals("120", m.conflicts[0].dropped.value)
    }

    @Test fun `different fields edited on each device are both kept without conflict`() {
        val m = ConflictMerge.merge(mapOf("name" to f("Soda 500ml", 10)), mapOf("price" to f("60", 12, "B")))
        assertEquals(setOf("name", "price"), m.fields.keys)
        assertTrue(m.conflicts.isEmpty())
    }

    @Test fun `the same value on both sides is not a conflict`() {
        val m = ConflictMerge.merge(mapOf("phone" to f("0712", 10)), mapOf("phone" to f("0712", 30, "B")))
        assertTrue(m.conflicts.isEmpty()); assertEquals(30, m.fields.getValue("phone").stamp.at)
    }

    @Test fun `merging gives the same answer whichever device merges`() {
        val a = mapOf("name" to f("X", 5), "price" to f("1", 9, "A"))
        val b = mapOf("name" to f("Y", 5, "B"), "price" to f("2", 8, "B"), "unit" to f("kg", 1, "B"))
        assertEquals(ConflictMerge.merge(a, b).fields, ConflictMerge.merge(b, a).fields)
    }

    @Test fun `equal times are settled by device id so both sides agree`() {
        val m = ConflictMerge.merge(mapOf("n" to f("one", 7, "A")), mapOf("n" to f("two", 7, "B")))
        assertEquals("two", m.fields.getValue("n").value)
    }

    @Test fun `stock changes on two devices add up instead of overwriting`() {
        // 50 in stock; one till sold 3, the other sold 5.
        assertEquals(42, ConflictMerge.mergeCounter(50, 47, 45))
    }

    @Test fun `credit moves in opposite directions are both counted`() {
        assertEquals(1_100, ConflictMerge.mergeCounter(1_000, 1_300, 800))
    }

    @Test fun `reversal of a completed sale applies`() =
        assertEquals(ReversalOutcome.APPLY, ConflictMerge.resolveReversal(true, "COMPLETED", "REFUNDED"))

    @Test fun `repeating the same reversal is harmless`() =
        assertEquals(ReversalOutcome.ALREADY_DONE, ConflictMerge.resolveReversal(false, "REFUNDED", "REFUNDED"))

    @Test fun `refund against a void is flagged and never applied twice`() =
        assertEquals(ReversalOutcome.CONFLICT, ConflictMerge.resolveReversal(false, "VOIDED", "REFUNDED"))
}
