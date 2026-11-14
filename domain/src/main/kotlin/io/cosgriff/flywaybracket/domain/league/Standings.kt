package io.cosgriff.flywaybracket.domain.league

import io.cosgriff.flywaybracket.domain.bracket.PickScore

/**
 * What a player's picks have earned so far in a league (#37).
 *
 * @property rightPicks how many of the decided matchups the player picked the winner of
 */
data class Score(val points: Int, val rightPicks: Int) {
    companion object {
        val NONE = Score(0, 0)

        /** The sum of what each of a player's picks earned; a matchup with no pick is [PickScore.MISSED]. */
        fun of(picks: List<PickScore>): Score = Score(points = picks.sumOf { it.total }, rightPicks = picks.count { it.roundPoints > 0 })
    }
}

/**
 * One row of a league table.
 *
 * @property player the player's index in the list given to [Standings.table]
 * @property rank 1 is first
 */
data class Place(val player: Int, val rank: Int)

/** A league table (#37). */
object Standings {
    /**
     * The table, best first: most points, then most right picks, then whoever joined the league first.
     *
     * Players level on points and on right picks share a rank, and the rank after them skips the places they
     * share: 10, 7, 7, 3 points rank 1, 2, 2, 4. Joining first lists a player higher within a shared rank;
     * it does not give them a better rank, since it says nothing of how they played.
     *
     * @param scores each player's score, in the order they joined the league, earliest first
     */
    fun table(scores: List<Score>): List<Place> {
        val best = compareByDescending<Score> { it.points }.thenByDescending { it.rightPicks }
        return scores.indices
            .sortedWith(compareBy(best) { i: Int -> scores[i] }.thenBy { it })
            .map { i -> Place(player = i, rank = 1 + scores.count { best.compare(it, scores[i]) < 0 }) }
    }
}
