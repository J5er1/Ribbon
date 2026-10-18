package app.readribbon.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// SeatingTests.swift's tests, case for case.

class SeatingTest {
    @Test
    fun testNobodyIsNoRows() {
        assertEquals(emptyList<Int>(), Seating.rows(places = 0))
    }

    @Test
    fun testUpToSixSitInOneRow() {
        for (places in 1..6) {
            assertEquals(listOf(places), Seating.rows(places = places))
        }
    }

    @Test
    fun testPastSixTheySitInTwoRowsAsEvenlyAsTheyGo() {
        assertEquals(listOf(4, 3), Seating.rows(places = 7))
        assertEquals(listOf(4, 4), Seating.rows(places = 8))
        assertEquals(listOf(5, 4), Seating.rows(places = 9))
        assertEquals(listOf(6, 5), Seating.rows(places = 11))
        assertEquals(listOf(6, 6), Seating.rows(places = 12))
    }

    @Test
    fun testEveryPlaceIsSeatedAndNoRowHoldsMoreThanSix() {
        for (places in 0..(Room.capacity + 1)) {
            val rows = Seating.rows(places = places)
            assertEquals(places, rows.sum())
            assertTrue(rows.all { it <= Seating.perRow })
            // The longer row is nearer the fire, and the rows differ by one
            // at most.
            assertEquals(rows.sortedDescending(), rows)
            if (rows.isNotEmpty()) {
                assertTrue(rows.first() - rows.last() <= 1)
            }
        }
    }
}
