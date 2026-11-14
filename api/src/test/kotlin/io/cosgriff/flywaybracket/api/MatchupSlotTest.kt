package io.cosgriff.flywaybracket.api

import io.cosgriff.flywaybracket.domain.bracket.Round
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class MatchupSlotTest {
    @Test
    fun `slots count by round, then by position from the top`() {
        val expected = Round.entries.flatMap { round ->
            List(round.teams / 2) { position -> round to position }
        }

        assertEquals(63, expected.size)
        assertEquals(expected, MatchupSlot.all.map { it.round to it.position })
        assertEquals((0..62).toList(), MatchupSlot.all.map { it.index })
    }

    @Test
    fun `each round starts where the one before it ended`() {
        assertEquals(
            listOf(0, 32, 48, 56, 60, 62),
            Round.entries.map { MatchupSlot.of(it, 0).index },
        )
        assertEquals(31, MatchupSlot.of(Round.ROUND_OF_64, 31).index)
        assertEquals(61, MatchupSlot.of(Round.SEMIFINAL, 1).index)
    }

    @Test
    fun `round and position give the slot back`() {
        MatchupSlot.all.forEach { assertEquals(it, MatchupSlot.of(it.round, it.position)) }
    }

    @Test
    fun `two neighbors from the top feed one matchup of the next round`() {
        assertEquals(MatchupSlot(32), MatchupSlot(0).next)
        assertEquals(MatchupSlot(32), MatchupSlot(1).next)
        assertEquals(MatchupSlot(33), MatchupSlot(2).next)
        assertEquals(MatchupSlot(47), MatchupSlot(31).next)
        assertEquals(MatchupSlot(62), MatchupSlot(60).next)
        assertEquals(MatchupSlot(62), MatchupSlot(61).next)
        assertNull(MatchupSlot(62).next)
    }

    @Test
    fun `every matchup but the first round's is fed by exactly two`() {
        val feeders = MatchupSlot.all.groupingBy { it.next }.eachCount()

        MatchupSlot.all.filter { it.round != Round.ROUND_OF_64 }.forEach { assertEquals(2, feeders[it]) }
    }

    @Test
    fun `a slot outside the bracket is refused`() {
        assertThrows(IllegalArgumentException::class.java) { MatchupSlot(-1) }
        assertThrows(IllegalArgumentException::class.java) { MatchupSlot(63) }
        assertThrows(IllegalArgumentException::class.java) { MatchupSlot.of(Round.FINAL, 1) }
        assertThrows(IllegalArgumentException::class.java) { MatchupSlot.of(Round.ROUND_OF_64, 32) }
    }

    @Test
    fun `slots sort in bracket order`() {
        assertEquals(MatchupSlot.all, MatchupSlot.all.shuffled(java.util.Random(1)).sorted())
    }
}
