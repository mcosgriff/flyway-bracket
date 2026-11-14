package io.cosgriff.flywaybracket.domain.bracket

/** What checking a player's picks against a field gave: a bracket they could fill in, or the first pick that is not. */
sealed interface PicksCheck {
    /** Every pick can stand: the picks are all or part of a bracket. */
    data object Consistent : PicksCheck

    /** The bird picked at [at] can never play there: it is not one of the field's birds drawn into that matchup. */
    data class NotInMatchup(val at: DrawPosition) : PicksCheck

    /**
     * The bird picked at [at] could play there, but is not picked to win the matchup before it on its path,
     * so it is not picked to get there.
     */
    data class NotPickedToReach(val at: DrawPosition) : PicksCheck
}

/**
 * A player's picks for a league's bracket (#35): for each matchup, the common name of the bird picked to win
 * it. A player may pick some matchups and leave others; a matchup with no pick scores nothing. A bird picked
 * to win a matchup after the first round must be picked to win every earlier matchup on its path.
 */
object BracketPicks {
    /**
     * Checks [picks] against a field, matchup by matchup in bracket order: by round, then from the top. The
     * first pick that cannot stand is the answer.
     *
     * @param teams the field's 64 birds ([FieldResult.Seeded.teams])
     */
    fun check(teams: List<Team>, picks: Map<DrawPosition, String>): PicksCheck {
        val drawn = Field.drawOrder(teams).map { it.commonName }
        for ((at, bird) in picks.entries.sortedWith(compareBy({ it.key.round.ordinal }, { it.key.position }))) {
            val index = drawn.indexOf(bird)
            if (!at.isInBracket() || index !in birdsOf(at)) return PicksCheck.NotInMatchup(at)
            val before = feederOf(at, index) ?: continue
            if (picks[before] != bird) return PicksCheck.NotPickedToReach(at)
        }
        return PicksCheck.Consistent
    }

    /**
     * The picks left when those for [changed] are dropped: the picks of those matchups, and every later pick
     * that depended on one of them, because it named the bird that dropped pick had win.
     */
    fun withoutChanged(picks: Map<DrawPosition, String>, changed: Collection<DrawPosition>): Map<DrawPosition, String> {
        val dropped = HashSet<DrawPosition>()
        val kept = LinkedHashMap<DrawPosition, String>()
        for ((at, bird) in picks.entries.sortedWith(compareBy({ it.key.round.ordinal }, { it.key.position }))) {
            val dependsOnDropped = feedersOf(at).any { it in dropped && picks[it] == bird }
            if (at in changed || dependsOnDropped) dropped += at else kept[at] = bird
        }
        return kept
    }

    /**
     * The picks after the player picks [bird] to win [at], or clears the pick there when [bird] is null (#41).
     * Every later pick that depended on the bird [at] had before is dropped with it, as [withoutChanged] drops
     * them; picking the bird already picked changes nothing. The result is not checked: see [check].
     */
    fun withPick(picks: Map<DrawPosition, String>, at: DrawPosition, bird: String?): Map<DrawPosition, String> {
        if (picks[at] == bird) return picks
        val kept = withoutChanged(picks, listOf(at))
        return if (bird == null) kept else kept + (at to bird)
    }

    /**
     * The later picks that picking [bird] to win [at] would clear ([withPick]), so the bracket screen can say
     * which before it changes them (#42): every pick other than the one at [at] that [withPick] drops. Empty
     * when the change clears nothing else.
     */
    fun clearedBy(picks: Map<DrawPosition, String>, at: DrawPosition, bird: String?): Set<DrawPosition> {
        val after = withPick(picks, at, bird)
        return picks.keys.filterTo(LinkedHashSet()) { it != at && it !in after }
    }

    private fun DrawPosition.isInBracket(): Boolean = position in 0 until round.teams / 2

    /** How many of the 64 birds, in [Field.drawOrder], can reach a matchup of [round]. */
    private fun birdsPerMatchup(round: Round): Int = Field.SIZE / (round.teams / 2)

    /** The indexes in [Field.drawOrder] of the birds that can play at [at]. */
    private fun birdsOf(at: DrawPosition): IntRange {
        val size = birdsPerMatchup(at.round)
        return at.position * size until (at.position + 1) * size
    }

    /** The two matchups whose winners meet at [at]; none in the first round. */
    private fun feedersOf(at: DrawPosition): List<DrawPosition> {
        val earlier = Round.entries.getOrNull(at.round.ordinal - 1) ?: return emptyList()
        return listOf(DrawPosition(earlier, 2 * at.position), DrawPosition(earlier, 2 * at.position + 1))
    }

    /** The matchup before [at] that the bird at [index] in [Field.drawOrder] plays on its way; none in the first round. */
    private fun feederOf(at: DrawPosition, index: Int): DrawPosition? {
        val earlier = Round.entries.getOrNull(at.round.ordinal - 1) ?: return null
        return DrawPosition(earlier, index / birdsPerMatchup(earlier))
    }
}
