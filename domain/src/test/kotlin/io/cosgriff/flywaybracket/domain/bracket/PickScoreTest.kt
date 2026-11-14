package io.cosgriff.flywaybracket.domain.bracket

import io.cosgriff.flywaybracket.domain.bracket.Decision.Reason
import org.junit.Assert.assertEquals
import org.junit.Test

class PickScoreTest {
    private val five = Team("Test Goldfinch", "Songbirds", seed = 5)
    private val twelve = Team("Test Vireo", "Songbirds", seed = 12)
    private val upset = Decision(winner = twelve, loser = five, reason = Reason.HIGHER_SHARE)
    private val chalk = Decision(winner = five, loser = twelve, reason = Reason.HIGHER_SHARE)

    @Test
    fun `rounds are worth 1, 2, 4, 8, 16 and 32`() {
        assertEquals(listOf(1, 2, 4, 8, 16, 32), Round.entries.map { it.points })
        assertEquals(listOf(64, 32, 16, 8, 4, 2), Round.entries.map { it.teams })
    }

    @Test
    fun `a right pick earns the round's points`() {
        assertEquals(PickScore(roundPoints = 1, upsetBonus = 0), PickScore.of(Round.ROUND_OF_64, "Test Goldfinch", chalk))
        assertEquals(8, PickScore.of(Round.QUARTERFINAL, "Test Goldfinch", chalk).total)
    }

    @Test
    fun `calling an upset adds the seed gap`() {
        // A 12 over a 5 in the first round: 1 point and 7 more.
        val score = PickScore.of(Round.ROUND_OF_64, "Test Vireo", upset)
        assertEquals(PickScore(roundPoints = 1, upsetBonus = 7), score)
        assertEquals(8, score.total)
    }

    @Test
    fun `the bonus is the same in a later round`() {
        val four = Team("Test Grackle", "Songbirds", seed = 4)
        val later = Decision(winner = twelve, loser = four, reason = Reason.HIGHER_SHARE)
        assertEquals(PickScore(roundPoints = 2, upsetBonus = 8), PickScore.of(Round.ROUND_OF_32, "Test Vireo", later))
    }

    @Test
    fun `a wrong pick earns nothing, upset or not`() {
        assertEquals(PickScore.MISSED, PickScore.of(Round.ROUND_OF_64, "Test Goldfinch", upset))
        assertEquals(PickScore.MISSED, PickScore.of(Round.FINAL, "Test Vireo", chalk))
        assertEquals(0, PickScore.MISSED.total)
    }

    @Test
    fun `equal seeds from two quarters carry no bonus`() {
        val a = Team("Alpha Bird", "Songbirds", seed = 1)
        val b = Team("Beta Bird", "Waterfowl", seed = 1)
        val final = Decision(winner = b, loser = a, reason = Reason.HIGHER_SHARE)
        assertEquals(PickScore(roundPoints = 32, upsetBonus = 0), PickScore.of(Round.FINAL, "Beta Bird", final))
    }
}
