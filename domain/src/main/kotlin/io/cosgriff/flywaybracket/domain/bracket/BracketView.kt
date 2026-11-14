package io.cosgriff.flywaybracket.domain.bracket

import io.cosgriff.flywaybracket.domain.league.Score

/**
 * How one matchup was decided, as the server reports it (#37): the bird that advanced, each bird's tally,
 * why, and what the player's pick of the matchup earned ([PickScore.MISSED] for a wrong pick or none).
 *
 * @property winner the common name of the bird that advanced
 */
data class DecidedMatchup(
    val winner: String,
    val upper: Tally,
    val lower: Tally,
    val reason: Decision.Reason,
    val score: PickScore,
)

/** One side of a matchup as the player sees it (#41). */
sealed interface Side {
    /** The bird is known: drawn here in the first round, or the winner of the matchup before. */
    data class Known(val team: Team) : Side

    /**
     * Not known yet: the matchup before, [from], is not decided, and the player picked [team] to win it. It
     * stands in for the real winner, marked as the player's pick, until that matchup is decided.
     *
     * @property out whether [team] has already lost a matchup, so it cannot get here
     */
    data class YourPick(val team: Team, val from: DrawPosition, val out: Boolean) : Side

    /** Not known yet, and the player has not picked who wins [from]: the winner of that matchup. */
    data class WinnerOf(val from: DrawPosition) : Side
}

/** Where the player's pick of one matchup stands. */
enum class PickStatus {
    /** No pick: the matchup scores nothing. */
    NONE,

    /** Not decided, and the picked bird has not lost. */
    ALIVE,

    /** Not decided, but the picked bird has already lost an earlier matchup: the pick cannot score. */
    OUT,

    /** Decided, and the picked bird won. */
    RIGHT,

    /** Decided, and the picked bird lost. */
    WRONG,
}

/**
 * One matchup of the player's bracket (#41).
 *
 * @property pick the bird the player picked to win it; null when they picked none
 * @property winner the bird that advanced; null until the matchup is decided
 * @property result how it was decided; null until then
 */
data class MatchupView(
    val at: DrawPosition,
    val upper: Side,
    val lower: Side,
    val pick: Team?,
    val pickStatus: PickStatus,
    val winner: Team?,
    val result: DecidedMatchup?,
) {
    /** What the pick earned; null until the matchup is decided. */
    val score: PickScore? get() = result?.score
}

/**
 * The player's bracket as the bracket screens show it (#41), built from the league's 64 birds, the player's
 * picks and the matchups decided so far: all 63 matchups, by round, then from the top.
 *
 * A first-round matchup has its two birds. A later one has, on each side, the winner of the matchup before
 * once it is decided; until then the bird the player picked to win it ([Side.YourPick]), or, with no pick,
 * the winner to come ([Side.WinnerOf]).
 */
data class BracketView(val teams: List<Team>, val matchups: List<MatchupView>) {
    /** The matchup at [at]. */
    fun matchup(at: DrawPosition): MatchupView = matchups[indexOf(at)]

    /** The matchups of [round], from the top. */
    fun round(round: Round): List<MatchupView> = matchups.filter { it.at.round == round }

    /** How many of the 63 matchups the player has picked. */
    val picks: Int get() = matchups.count { it.pick != null }

    /** What the player's picks have earned so far. */
    val score: Score get() = Score.of(matchups.mapNotNull { it.score })

    /** How many matchups are decided. */
    val decided: Int get() = matchups.count { it.result != null }

    /**
     * The birds the player picked to win a matchup of [round] that is not decided, and that have not lost:
     * their picks still alive in the round.
     */
    fun alivePicks(round: Round): List<Team> =
        round(round).filter { it.pickStatus == PickStatus.ALIVE }.mapNotNull { it.pick }

    /**
     * The player's picks still alive in [round] ([alivePicks]) that they have not logged in it: Home's and the
     * Summary screen's "not seen yet" (#26).
     *
     * @param logged the common names of the birds the player has a sighting of dated within the round
     */
    fun picksNotYetSeen(round: Round, logged: Set<String>): List<Team> =
        alivePicks(round).filter { it.commonName !in logged }

    companion object {
        /** Matchups in a 64-bird bracket. */
        const val MATCHUPS = Field.SIZE - 1

        /**
         * The view of a bracket.
         *
         * @param teams the league's 64 birds
         * @param picks the common name of the bird the player picked to win each matchup they picked. A pick
         *   naming a bird not in [teams] is left out.
         * @param results the matchups decided so far
         */
        fun of(teams: List<Team>, picks: Map<DrawPosition, String>, results: Map<DrawPosition, DecidedMatchup>): BracketView {
            require(teams.size == Field.SIZE) { "A bracket has ${Field.SIZE} birds, not ${teams.size}" }
            val byName = teams.associateBy { it.commonName }
            val drawn = Field.drawOrder(teams)
            val built = ArrayList<MatchupView>(MATCHUPS)
            // Birds that have lost a matchup. Rounds are built in order, so a round sees every earlier loss.
            val lost = HashSet<String>()

            fun side(from: DrawPosition): Side {
                built[indexOf(from)].winner?.let { return Side.Known(it) }
                val picked = picks[from]?.let(byName::get) ?: return Side.WinnerOf(from)
                return Side.YourPick(picked, from, out = picked.commonName in lost)
            }

            for (round in Round.entries) {
                val roundLosers = HashSet<String>()
                repeat(round.teams / 2) { position ->
                    val at = DrawPosition(round, position)
                    val (upper, lower) = if (round == Round.ROUND_OF_64) {
                        Side.Known(drawn[2 * position]) to Side.Known(drawn[2 * position + 1])
                    } else {
                        val earlier = Round.entries[round.ordinal - 1]
                        side(DrawPosition(earlier, 2 * position)) to side(DrawPosition(earlier, 2 * position + 1))
                    }
                    // A result naming a bird not in the field is not one this bracket can show: left undecided.
                    val result = results[at]?.takeIf { it.winner in byName }
                    val winner = result?.let { byName.getValue(it.winner) }
                    if (winner != null) {
                        listOf(upper, lower).filterIsInstance<Side.Known>().map { it.team.commonName }
                            .filterTo(roundLosers) { it != winner.commonName }
                    }
                    val pick = picks[at]?.let(byName::get)
                    val status = when {
                        pick == null -> PickStatus.NONE
                        winner != null -> if (pick == winner) PickStatus.RIGHT else PickStatus.WRONG
                        pick.commonName in lost -> PickStatus.OUT
                        else -> PickStatus.ALIVE
                    }
                    built += MatchupView(at, upper, lower, pick, status, winner, result)
                }
                lost += roundLosers
            }
            return BracketView(teams, built)
        }

        private fun indexOf(at: DrawPosition): Int {
            require(at.position in 0 until at.round.teams / 2) { "${at.round} has no position ${at.position}" }
            return Round.entries.take(at.round.ordinal).sumOf { it.teams / 2 } + at.position
        }
    }
}
