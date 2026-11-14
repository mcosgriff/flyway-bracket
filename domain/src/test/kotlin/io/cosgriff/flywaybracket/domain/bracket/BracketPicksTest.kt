package io.cosgriff.flywaybracket.domain.bracket

import org.junit.Assert.assertEquals
import org.junit.Test

// Made-up birds: `Test <quarter> <seed>`. No name or figure is real.
class BracketPicksTest {
    private val teams = Field.quarters.flatMap { quarter ->
        (1..Seeding.PER_QUARTER).map { seed -> Team("Test $quarter ${seed.toString().padStart(2, '0')}", quarter, seed) }
    }

    /** The birds top to bottom, as the bracket draws them. */
    private val drawn = Field.drawOrder(teams).map { it.commonName }

    private fun at(round: Round, position: Int) = DrawPosition(round, position)

    /** A whole bracket in which the bird drawn above wins every matchup. */
    private fun upperWinsEverywhere(): Map<DrawPosition, String> {
        val picks = LinkedHashMap<DrawPosition, String>()
        Round.entries.forEach { round ->
            repeat(round.teams / 2) { position ->
                picks[at(round, position)] = if (round == Round.ROUND_OF_64) {
                    drawn[2 * position]
                } else {
                    picks.getValue(at(Round.entries[round.ordinal - 1], 2 * position))
                }
            }
        }
        return picks
    }

    @Test
    fun `a whole bracket is consistent`() {
        val picks = upperWinsEverywhere()

        assertEquals(63, picks.size)
        assertEquals(PicksCheck.Consistent, BracketPicks.check(teams, picks))
    }

    @Test
    fun `no picks, or some, are consistent`() {
        val whole = upperWinsEverywhere()

        assertEquals(PicksCheck.Consistent, BracketPicks.check(teams, emptyMap()))
        assertEquals(PicksCheck.Consistent, BracketPicks.check(teams, whole.filterKeys { it.round == Round.ROUND_OF_64 }))
        // The top quarter all the way to its last game, and nothing else.
        assertEquals(
            PicksCheck.Consistent,
            BracketPicks.check(teams, whole.filterKeys { it.round <= Round.QUARTERFINAL && it.position < it.round.teams / 8 }),
        )
    }

    @Test
    fun `a later pick needs the bird picked in every earlier matchup on its path`() {
        val whole = upperWinsEverywhere()
        val final = at(Round.FINAL, 0)
        val champion = whole.getValue(final)

        // The champion's quarterfinal pick is missing.
        assertEquals(
            PicksCheck.NotPickedToReach(at(Round.SEMIFINAL, 0)),
            BracketPicks.check(teams, whole - at(Round.QUARTERFINAL, 0)),
        )
        // Only the final is picked.
        assertEquals(PicksCheck.NotPickedToReach(final), BracketPicks.check(teams, mapOf(final to champion)))
    }

    @Test
    fun `a later pick of a bird picked to lose earlier is not consistent`() {
        val whole = upperWinsEverywhere()
        // The bird drawn second loses the first matchup, then is picked to win the next.
        val loser = drawn[1]

        val picks = whole + (at(Round.ROUND_OF_32, 0) to loser)

        assertEquals(PicksCheck.NotPickedToReach(at(Round.ROUND_OF_32, 0)), BracketPicks.check(teams, picks))
    }

    @Test
    fun `a bird that cannot play in a matchup is not in it`() {
        val whole = upperWinsEverywhere()

        // The bird drawn third plays in the second matchup, not the first.
        assertEquals(PicksCheck.NotInMatchup(at(Round.ROUND_OF_64, 0)), BracketPicks.check(teams, whole + (at(Round.ROUND_OF_64, 0) to drawn[2])))
        // A bird of the bottom quarter cannot win the top one's quarterfinal.
        assertEquals(
            PicksCheck.NotInMatchup(at(Round.QUARTERFINAL, 0)),
            BracketPicks.check(teams, whole + (at(Round.QUARTERFINAL, 0) to drawn.last())),
        )
        // No such bird in the field, or no such matchup.
        assertEquals(PicksCheck.NotInMatchup(at(Round.FINAL, 0)), BracketPicks.check(teams, whole + (at(Round.FINAL, 0) to "Test Unknown Bird")))
        assertEquals(PicksCheck.NotInMatchup(at(Round.FINAL, 1)), BracketPicks.check(teams, mapOf(at(Round.FINAL, 1) to drawn[0])))
    }

    @Test
    fun `the first pick that cannot stand is the earliest in bracket order`() {
        val whole = upperWinsEverywhere()
        val picks = whole + (at(Round.ROUND_OF_32, 3) to drawn.last()) + (at(Round.ROUND_OF_64, 20) to drawn[0])

        assertEquals(PicksCheck.NotInMatchup(at(Round.ROUND_OF_64, 20)), BracketPicks.check(teams, picks))
    }

    @Test
    fun `dropping changed matchups drops the picks that depended on them`() {
        val whole = upperWinsEverywhere()
        val first = at(Round.ROUND_OF_64, 0)

        val left = BracketPicks.withoutChanged(whole, listOf(first))

        // The bird picked in the first matchup is the champion: every pick of it goes.
        val dropped = whole.keys - left.keys
        assertEquals(Round.entries.map { at(it, 0) }.toSet(), dropped)
        assertEquals(whole - dropped, left)
        assertEquals(PicksCheck.Consistent, BracketPicks.check(teams, left))
    }

    @Test
    fun `a later pick of the other feeder's bird is kept`() {
        val whole = upperWinsEverywhere()
        // The second matchup's bird wins the next one instead.
        val picks = whole + (at(Round.ROUND_OF_32, 0) to drawn[2]) - Round.entries.drop(2).map { at(it, 0) }.toSet()

        val left = BracketPicks.withoutChanged(picks, listOf(at(Round.ROUND_OF_64, 0)))

        assertEquals(picks - at(Round.ROUND_OF_64, 0), left)
    }

    @Test
    fun `a changed matchup with no pick drops nothing`() {
        val picks = upperWinsEverywhere().filterKeys { it.round == Round.ROUND_OF_64 && it.position > 0 }

        assertEquals(picks, BracketPicks.withoutChanged(picks, listOf(at(Round.ROUND_OF_64, 0), at(Round.FINAL, 0))))
        assertEquals(emptyMap<DrawPosition, String>(), BracketPicks.withoutChanged(emptyMap(), listOf(at(Round.ROUND_OF_64, 0))))
    }

    @Test
    fun `changing a pick says which later picks it clears`() {
        val whole = upperWinsEverywhere()
        val first = at(Round.ROUND_OF_64, 0)

        // The top bird is picked to win every round: picking the other bird clears all five later picks.
        assertEquals(Round.entries.drop(1).map { at(it, 0) }.toSet(), BracketPicks.clearedBy(whole, first, drawn[1]))
        // So does clearing the pick.
        assertEquals(Round.entries.drop(1).map { at(it, 0) }.toSet(), BracketPicks.clearedBy(whole, first, null))
        // Picking the bird already picked, or in a matchup no later pick depends on, clears nothing else.
        assertEquals(emptySet<DrawPosition>(), BracketPicks.clearedBy(whole, first, drawn[0]))
        assertEquals(emptySet<DrawPosition>(), BracketPicks.clearedBy(whole, at(Round.ROUND_OF_64, 1), drawn[3]))
        assertEquals(emptySet<DrawPosition>(), BracketPicks.clearedBy(emptyMap(), first, drawn[1]))
    }
}
