package io.cosgriff.flywaybracket.domain.league

import org.junit.Assert.assertEquals
import org.junit.Test

class StandingsTest {
    @Test
    fun `more points rank higher`() {
        assertEquals(listOf(2, 1, 3), Standings.ranks(listOf(5, 9, 1)))
    }

    @Test
    fun `players level on points share a rank, and the next rank skips`() {
        assertEquals(listOf(1, 2, 2, 4), Standings.ranks(listOf(10, 7, 7, 3)))
    }

    @Test
    fun `everyone on no points is first`() {
        assertEquals(listOf(1, 1, 1), Standings.ranks(listOf(0, 0, 0)))
        assertEquals(emptyList<Int>(), Standings.ranks(emptyList()))
    }
}
