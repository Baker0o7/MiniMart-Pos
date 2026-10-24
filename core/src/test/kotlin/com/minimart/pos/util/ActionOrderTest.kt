package com.minimart.pos.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ActionOrderTest {
    private val all = listOf("a", "b", "c", "d")

    @Test fun noSavedOrderKeepsDefaults() = assertEquals(all, ActionOrder.apply(all, emptyList()))
    @Test fun savedOrderIsFollowed() = assertEquals(listOf("c", "a", "b", "d"), ActionOrder.apply(all, listOf("c", "a")))
    @Test fun unknownAndRepeatedIdsAreIgnored() = assertEquals(listOf("b", "a", "c", "d"), ActionOrder.apply(all, listOf("b", "zzz", "b", "a")))
    @Test fun newTilesGoAtTheEnd() = assertEquals(listOf("b", "a", "c", "d", "e"), ActionOrder.apply(all + "e", listOf("b", "a")))
    @Test fun hiddenTilesAreSimplyLeftOut() = assertEquals(listOf("c", "a"), ActionOrder.apply(listOf("a", "c"), listOf("c", "b", "a")))

    @Test fun moveForwardAndBack() {
        assertEquals(listOf("b", "c", "a", "d"), ActionOrder.moveTo(all, "a", "c"))
        assertEquals(listOf("d", "a", "b", "c"), ActionOrder.moveTo(all, "d", "a"))
    }
    @Test fun moveOntoItselfOrUnknownChangesNothing() {
        assertEquals(all, ActionOrder.moveTo(all, "b", "b"))
        assertEquals(all, ActionOrder.moveTo(all, "x", "b"))
        assertEquals(all, ActionOrder.moveTo(all, "b", "x"))
    }
    @Test fun parseAndEncodeRoundTrip() {
        assertEquals(listOf("a", "b"), ActionOrder.parse(" a, b,,a "))
        assertEquals("a,b", ActionOrder.encode(listOf("a", "b", "a")))
        assertEquals(emptyList<String>(), ActionOrder.parse(""))
    }
    @Test fun mergeKeepsHiddenTilesAfterTheVisibleOnes() =
        assertEquals(listOf("c", "a", "b"), ActionOrder.merge(listOf("c", "a"), listOf("a", "b", "c")))
}
