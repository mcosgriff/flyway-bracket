package io.cosgriff.flywaybracket.domain.bracket

import io.cosgriff.flywaybracket.domain.bracket.Decision.Reason
import io.cosgriff.flywaybracket.domain.pack.TimeBin
import io.cosgriff.flywaybracket.domain.pack.chart
import io.cosgriff.flywaybracket.domain.pack.seasonal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MatchupRuleTest {
    private val four = Team("Test Grackle", "Songbirds", seed = 4)
    private val twelve = Team("Test Vireo", "Songbirds", seed = 12)
    private val game = Matchup(upper = four, lower = twelve)

    @Test
    fun `the bird logged by the larger share of its eligible players advances`() {
        // 6 of 12 is 50%; 6 of 9 is 67%. Fewer players could see the vireo, but more of them did.
        val decision = MatchupRule.decide(game, upper = Tally(12, 6), lower = Tally(9, 6))
        assertEquals(twelve, decision.winner)
        assertEquals(four, decision.loser)
        assertEquals(Reason.HIGHER_SHARE, decision.reason)
        assertTrue(decision.isUpset)
    }

    @Test
    fun `raw counts do not decide it`() {
        // 10 of 40 loggers beat nobody on count, but 25% loses to 4 of 5.
        val decision = MatchupRule.decide(game, upper = Tally(40, 10), lower = Tally(5, 4))
        assertEquals(twelve, decision.winner)
    }

    @Test
    fun `equal shares go to the better seed`() {
        val decision = MatchupRule.decide(game, upper = Tally(12, 6), lower = Tally(8, 4))
        assertEquals(four, decision.winner)
        assertEquals(Reason.TIED_BETTER_SEED, decision.reason)
        assertFalse(decision.isUpset)
    }

    @Test
    fun `shares that differ by a hair are not a tie`() {
        // 333 of 1000 against 1 of 3: 33.3% is under 33.33…%.
        val decision = MatchupRule.decide(game, upper = Tally(1000, 333), lower = Tally(3, 1))
        assertEquals(twelve, decision.winner)
        assertEquals(Reason.HIGHER_SHARE, decision.reason)
    }

    @Test
    fun `a share from fewer than three eligible players does not count`() {
        // 2 of 2 is not 100%: the vireo's share counts as zero and the grackle's 1 of 10 wins.
        val decision = MatchupRule.decide(game, upper = Tally(10, 1), lower = Tally(2, 2))
        assertEquals(four, decision.winner)
        assertEquals(Reason.HIGHER_SHARE, decision.reason)
    }

    @Test
    fun `when neither bird has enough eligible players the better seed advances`() {
        val decision = MatchupRule.decide(game, upper = Tally(2, 0), lower = Tally(1, 1))
        assertEquals(four, decision.winner)
        assertEquals(Reason.TOO_FEW_ELIGIBLE, decision.reason)
    }

    @Test
    fun `nobody logging either bird is a tie for the better seed`() {
        val decision = MatchupRule.decide(game, upper = Tally(12, 0), lower = Tally(9, 0))
        assertEquals(four, decision.winner)
        assertEquals(Reason.TIED_BETTER_SEED, decision.reason)
    }

    @Test
    fun `the better seed wins a tie wherever it is drawn`() {
        val flipped = Matchup(upper = twelve, lower = four)
        assertEquals(four, MatchupRule.decide(flipped, upper = Tally(8, 4), lower = Tally(12, 6)).winner)
    }

    @Test
    fun `two quarter champions with the same seed tie on the name`() {
        val a = Team("Alpha Bird", "Songbirds", seed = 1)
        val b = Team("Beta Bird", "Waterfowl", seed = 1)
        val decision = MatchupRule.decide(Matchup(b, a), upper = Tally(10, 5), lower = Tally(10, 5))
        assertEquals(a, decision.winner)
        assertFalse(decision.isUpset)
    }

    @Test
    fun `a tally cannot log more players than were eligible`() {
        assertThrows(IllegalArgumentException::class.java) { Tally(eligible = 3, logged = 4) }
        assertEquals(0.0, Tally(0, 0).share, 0.0)
        assertEquals(0.5, Tally(12, 6).share, 0.0)
    }

    // Eligibility

    private val spring = listOf(TimeBin.of(5, 1), TimeBin.of(5, 2))
    private val foothills = chart(seasonal("Test Hummingbird", TimeBin.of(4, 4), TimeBin.of(9, 2), inSeason = 0.50))
    private val plains = chart(seasonal("Test Kingbird", TimeBin.of(5, 1), TimeBin.of(8, 4), inSeason = 0.60))

    @Test
    fun `a player is eligible for a bird expected in their home county that week`() {
        assertTrue(MatchupRule.isEligible("Test Hummingbird", spring, home = foothills))
        assertFalse(MatchupRule.isEligible("Test Kingbird", spring, home = foothills))
    }

    @Test
    fun `a trip adds the birds of the county visited`() {
        assertTrue(MatchupRule.isEligible("Test Kingbird", spring, home = foothills, visited = listOf(plains)))
        // And takes nothing away.
        assertTrue(MatchupRule.isEligible("Test Hummingbird", spring, home = foothills, visited = listOf(plains)))
    }

    @Test
    fun `a bird due in any part of the round counts`() {
        val arrivesMidRound = chart(seasonal("Test Oriole", TimeBin.of(5, 2), TimeBin.of(8, 4), inSeason = 0.20))
        assertTrue(MatchupRule.isEligible("Test Oriole", spring, home = arrivesMidRound))
        assertFalse(MatchupRule.isEligible("Test Oriole", listOf(TimeBin.of(5, 1)), home = arrivesMidRound))
    }
}
