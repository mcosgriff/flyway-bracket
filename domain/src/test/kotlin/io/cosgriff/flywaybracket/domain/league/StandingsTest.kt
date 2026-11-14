package io.cosgriff.flywaybracket.domain.league

import io.cosgriff.flywaybracket.domain.bracket.PickScore
import org.junit.Assert.assertEquals
import org.junit.Test

class StandingsTest {
    /** The table as (player, rank) pairs, best first. */
    private fun table(vararg scores: Score): List<Pair<Int, Int>> = Standings.table(scores.toList()).map { it.player to it.rank }

    @Test
    fun `more points rank higher`() {
        assertEquals(listOf(1 to 1, 0 to 2, 2 to 3), table(Score(5, 3), Score(9, 2), Score(1, 1)))
    }

    @Test
    fun `players level on points share a rank, and the next rank skips`() {
        assertEquals(
            listOf(0 to 1, 1 to 2, 2 to 2, 3 to 4),
            table(Score(10, 4), Score(7, 3), Score(7, 3), Score(3, 1)),
        )
    }

    @Test
    fun `level on points, more right picks rank higher`() {
        // A long-shot upset can earn as much as several right picks; the steadier player ranks first.
        assertEquals(listOf(1 to 1, 0 to 2), table(Score(17, 2), Score(17, 5)))
    }

    @Test
    fun `level on both, the earlier joiner is listed first in a shared rank`() {
        assertEquals(listOf(0 to 1, 1 to 1, 2 to 1), table(Score(6, 3), Score(6, 3), Score(6, 3)))
        assertEquals(listOf(1 to 1, 0 to 2, 2 to 2), table(Score(2, 1), Score(4, 1), Score(2, 1)))
    }

    @Test
    fun `everyone on no points is first`() {
        assertEquals(listOf(0 to 1, 1 to 1, 2 to 1), table(Score.NONE, Score.NONE, Score.NONE))
        assertEquals(emptyList<Place>(), Standings.table(emptyList()))
    }

    @Test
    fun `a score adds up the picks, counting only the right ones as right`() {
        val picks = listOf(PickScore(1, 0), PickScore.MISSED, PickScore(2, 7), PickScore(4, 0), PickScore.MISSED)

        assertEquals(Score(points = 14, rightPicks = 3), Score.of(picks))
        assertEquals(Score.NONE, Score.of(emptyList()))
    }
}
