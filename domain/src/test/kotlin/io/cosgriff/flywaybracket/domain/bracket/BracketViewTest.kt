package io.cosgriff.flywaybracket.domain.bracket

import io.cosgriff.flywaybracket.domain.league.Score
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

// Made-up birds (`testTeams()`) and made-up tallies. No name or figure is real.
class BracketViewTest {
    private val teams = testTeams()
    private val drawn = Field.drawOrder(teams)

    private fun at(round: Round, position: Int) = DrawPosition(round, position)

    /** A whole bracket picked for the better seed in every matchup. */
    private val favourites: Map<DrawPosition, String> =
        playBracket(teams) { _, upper, lower -> favourite(upper, lower) }.mapValues { it.value.commonName }

    /**
     * The results of [rounds], the better seed winning every matchup except those in [upsets], and each pick in
     * [picks] scored as `PickScore.of` scores it.
     */
    private fun results(rounds: Set<Round>, upsets: Set<DrawPosition>, picks: Map<DrawPosition, String>): Map<DrawPosition, DecidedMatchup> {
        val decided = LinkedHashMap<DrawPosition, DecidedMatchup>()
        playBracket(teams) { at, upper, lower ->
            val better = favourite(upper, lower)
            val winner = if (at in upsets) (if (better == upper) lower else upper) else better
            val loser = if (winner == upper) lower else upper
            if (at.round in rounds) {
                val decision = Decision(winner, loser, Decision.Reason.HIGHER_SHARE)
                val score = picks[at]?.let { PickScore.of(at.round, it, decision) } ?: PickScore.MISSED
                val (winning, losing) = Tally(eligible = 10, logged = 6) to Tally(eligible = 10, logged = 3)
                decided[at] = DecidedMatchup(
                    winner = winner.commonName,
                    upper = if (winner == upper) winning else losing,
                    lower = if (winner == lower) winning else losing,
                    reason = decision.reason,
                    score = score,
                )
            }
            winner
        }
        return decided
    }

    @Test
    fun `an empty bracket has its first round's birds and the winners to come`() {
        val view = BracketView.of(teams, picks = emptyMap(), results = emptyMap())

        assertEquals(63, view.matchups.size)
        assertEquals(listOf(32, 16, 8, 4, 2, 1), Round.entries.map { view.round(it).size })
        assertEquals(Side.Known(drawn[0]), view.matchup(at(Round.ROUND_OF_64, 0)).upper)
        assertEquals(Side.Known(drawn[63]), view.matchup(at(Round.ROUND_OF_64, 31)).lower)
        val second = view.matchup(at(Round.ROUND_OF_32, 3))
        assertEquals(Side.WinnerOf(at(Round.ROUND_OF_64, 6)), second.upper)
        assertEquals(Side.WinnerOf(at(Round.ROUND_OF_64, 7)), second.lower)
        assertEquals(Side.WinnerOf(at(Round.SEMIFINAL, 1)), view.matchup(at(Round.FINAL, 0)).lower)
        assertEquals(setOf(PickStatus.NONE), view.matchups.map { it.pickStatus }.toSet())
        assertEquals(0, view.picks)
        assertEquals(0, view.decided)
        assertEquals(Score.NONE, view.score)
        assertEquals(emptyList<Team>(), view.alivePicks(Round.ROUND_OF_64))
    }

    @Test
    fun `a full bracket shows the player's picked birds in every later round`() {
        val view = BracketView.of(teams, favourites, results = emptyMap())
        val songbirdsOne = teams.single { it.quarter == "SONGBIRDS" && it.seed == 1 }
        val fliersOne = teams.single { it.quarter == "FLIERS" && it.seed == 1 }

        assertEquals(63, view.picks)
        assertEquals(setOf(PickStatus.ALIVE), view.matchups.map { it.pickStatus }.toSet())
        val final = view.matchup(at(Round.FINAL, 0))
        assertEquals(Side.YourPick(songbirdsOne, at(Round.SEMIFINAL, 0), out = false), final.upper)
        assertEquals(Side.YourPick(fliersOne, at(Round.SEMIFINAL, 1), out = false), final.lower)
        assertEquals(fliersOne, final.pick)
        assertNull(final.winner)
        assertNull(final.score)
        assertEquals(32, view.alivePicks(Round.ROUND_OF_64).size)
        assertEquals(listOf(fliersOne), view.alivePicks(Round.FINAL))
    }

    @Test
    fun `picks not yet seen are the alive picks of the round the player has not logged`() {
        val view = BracketView.of(teams, favourites, results = emptyMap())
        val picked = view.alivePicks(Round.ROUND_OF_64)
        val logged = picked.take(10).map { it.commonName }.toSet() + "Test Not In The Field"

        assertEquals(picked.drop(10), view.picksNotYetSeen(Round.ROUND_OF_64, logged))
        assertEquals(picked, view.picksNotYetSeen(Round.ROUND_OF_64, emptySet()))
    }

    @Test
    fun `changing a round 1 pick that fed the final drops every later pick of that bird`() {
        val champion = teams.single { it.commonName == favourites.getValue(at(Round.FINAL, 0)) }
        val index = drawn.indexOf(champion)
        val first = at(Round.ROUND_OF_64, index / 2)
        val other = drawn[index xor 1]
        assertEquals(champion.commonName, favourites.getValue(first))

        val changed = BracketPicks.withPick(favourites, first, other.commonName)

        // The champion's picks in rounds 2 to 6 are gone; every other pick stays.
        val dropped = Round.entries.drop(1).map { at(it, first.position shr it.ordinal) }
        assertEquals(favourites.keys - dropped.toSet(), changed.keys)
        assertEquals(other.commonName, changed.getValue(first))
        assertEquals(PicksCheck.Consistent, BracketPicks.check(teams, changed))
        val view = BracketView.of(teams, changed, results = emptyMap())
        assertEquals(58, view.picks)
        val next = view.matchup(at(Round.ROUND_OF_32, first.position / 2))
        assertEquals(Side.YourPick(other, first, out = false), if (first.position % 2 == 0) next.upper else next.lower)
        val final = view.matchup(at(Round.FINAL, 0))
        assertNull(final.pick)
        // The champion came from the lower semifinal.
        assertEquals(Side.WinnerOf(at(Round.SEMIFINAL, 1)), final.lower)
    }

    @Test
    fun `picking the bird already picked changes nothing, and clearing a pick drops what depended on it`() {
        val first = at(Round.ROUND_OF_64, 0)
        // The top bird is picked through the semifinal, and loses the final.
        val itsPicks = Round.entries.dropLast(1).map { at(it, 0) }.toSet()

        assertSame(favourites, BracketPicks.withPick(favourites, first, favourites.getValue(first)))
        val cleared = BracketPicks.withPick(favourites, first, null)
        assertEquals(favourites.keys - itsPicks, cleared.keys)
        assertEquals(PicksCheck.Consistent, BracketPicks.check(teams, cleared))
    }

    @Test
    fun `a bracket halfway through a season shows the real winners, then the player's picks`() {
        // Rounds 1 to 3 decided. Two upsets: a 9 seed over the 8 at the second matchup, and in the round of 16
        // the top quarter's 4 seed over the 1 the player picked all the way to the final.
        val rounds = setOf(Round.ROUND_OF_64, Round.ROUND_OF_32, Round.ROUND_OF_16)
        val upsets = setOf(at(Round.ROUND_OF_64, 1), at(Round.ROUND_OF_16, 0))
        val view = BracketView.of(teams, favourites, results(rounds, upsets, favourites))
        val songbirdsOne = drawn[0]
        val songbirdsFour = teams.single { it.quarter == "SONGBIRDS" && it.seed == 4 }

        assertEquals(56, view.decided)
        val upset = view.matchup(at(Round.ROUND_OF_64, 1))
        assertEquals(drawn[3], upset.winner)
        assertEquals(PickStatus.WRONG, upset.pickStatus)
        assertEquals(PickScore.MISSED, upset.score)
        assertEquals(Tally(10, 6), upset.result?.lower)
        // The real winners replace the player's picks once their matchups are decided.
        assertEquals(Side.Known(drawn[3]), view.matchup(at(Round.ROUND_OF_32, 0)).lower)
        assertEquals(PickStatus.RIGHT, view.matchup(at(Round.ROUND_OF_32, 0)).pickStatus)
        assertEquals(PickScore(roundPoints = 2, upsetBonus = 0), view.matchup(at(Round.ROUND_OF_32, 0)).score)
        val quarterfinal = view.matchup(at(Round.QUARTERFINAL, 0))
        assertEquals(Side.Known(songbirdsFour), quarterfinal.upper)
        // The player's champion is out: every pick of it still to be decided is too.
        assertEquals(songbirdsOne, quarterfinal.pick)
        assertEquals(PickStatus.OUT, quarterfinal.pickStatus)
        assertEquals(PickStatus.OUT, view.matchup(at(Round.SEMIFINAL, 0)).pickStatus)
        assertEquals(Side.YourPick(songbirdsOne, at(Round.SEMIFINAL, 0), out = true), view.matchup(at(Round.FINAL, 0)).upper)
        assertEquals(PickStatus.ALIVE, view.matchup(at(Round.QUARTERFINAL, 1)).pickStatus)
        // 31 right in round 1, 16 in round 2, 7 in round 3; no right pick was an upset.
        assertEquals(Score(points = 31 * 1 + 16 * 2 + 7 * 4, rightPicks = 31 + 16 + 7), view.score)
        // The quarterfinals: three of the four picks are alive.
        assertEquals(3, view.alivePicks(Round.QUARTERFINAL).size)
        assertEquals(
            view.alivePicks(Round.QUARTERFINAL).drop(1),
            view.picksNotYetSeen(Round.QUARTERFINAL, setOf(view.alivePicks(Round.QUARTERFINAL).first().commonName)),
        )
    }

    @Test
    fun `a pick naming a bird not in the field, and a result naming one, are left out`() {
        val first = at(Round.ROUND_OF_64, 0)
        val stray = DecidedMatchup("Test Stray", Tally(10, 5), Tally(10, 4), Decision.Reason.HIGHER_SHARE, PickScore.MISSED)

        val view = BracketView.of(teams, mapOf(first to "Test Stray"), mapOf(first to stray))

        assertNull(view.matchup(first).pick)
        assertNull(view.matchup(first).result)
        assertEquals(Side.WinnerOf(first), view.matchup(at(Round.ROUND_OF_32, 0)).upper)
    }
}
