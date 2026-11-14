package io.cosgriff.flywaybracket.domain.bracket

import io.cosgriff.flywaybracket.domain.pack.CountyChart
import io.cosgriff.flywaybracket.domain.pack.TimeBin

/**
 * One bird's week in a league: of the [eligible] players who could have seen it, [logged] did.
 * A player counts once, however many of the bird they saw.
 */
data class Tally(val eligible: Int, val logged: Int) {
    init {
        require(eligible >= 0 && logged in 0..eligible) { "$logged of $eligible is not a tally" }
    }

    /** 0 to 1; zero when nobody was eligible. */
    val share: Double get() = if (eligible == 0) 0.0 else logged.toDouble() / eligible
}

/**
 * One player's round, as a tally reads it (#37): their home county, and their sightings dated within the
 * round, reduced to the counties and the birds.
 *
 * @property home the chart of their home county for the round; null when they have none, or none a pack
 *   holds
 * @property visited the charts of the counties where they have a sighting dated within the round
 * @property logged the common names of the birds they have a sighting of dated within the round, in any
 *   county
 */
data class PlayerRound(val home: CountyChart?, val visited: List<CountyChart> = emptyList(), val logged: Set<String> = emptySet())

/** Who advanced, and why, so the screen can say it in the player's language. */
data class Decision(val winner: Team, val loser: Team, val reason: Reason) {
    enum class Reason {
        /** The winner was logged by the larger share of its eligible players. */
        HIGHER_SHARE,

        /** The shares were equal, so the better seed advanced. */
        TIED_BETTER_SEED,

        /** Neither bird had enough eligible players for its share to count; the better seed advanced. */
        TOO_FEW_ELIGIBLE,
    }

    /** A lower-seeded bird beat a better-seeded one. */
    val isUpset: Boolean get() = winner.seed > loser.seed
}

/**
 * Decides a matchup when its week closes. The winner is the bird logged by the larger share of the
 * players who could see it, so no bird loses because it skips a member's region.
 */
object MatchupRule {
    /**
     * A share from fewer eligible players than this is too thin to win on (1 of 1 is not 100%). Such a
     * bird's share counts as zero.
     */
    const val MIN_ELIGIBLE = 3

    /**
     * Whether a player could have seen [commonName] in a round: it is expected, in any of the round's
     * [bins], in their home county or in any county they logged an outing in that week. A trip can
     * only add birds.
     *
     * @param home null when the player has no home county, or one no pack holds: then only a trip counts
     */
    fun isEligible(commonName: String, bins: List<TimeBin>, home: CountyChart?, visited: List<CountyChart> = emptyList()): Boolean =
        (visited + listOfNotNull(home)).any { county -> bins.any { bin -> county.isExpected(commonName, bin) } }

    /**
     * One bird's tally for a round (#37): of the [players] eligible for it ([isEligible]), how many logged
     * it. A player who logged the bird but was not eligible for it is not counted at all (#17).
     *
     * @param bins the round's bins (`SeasonCalendar.bins`)
     */
    fun tally(commonName: String, bins: List<TimeBin>, players: List<PlayerRound>): Tally {
        val eligible = players.filter { isEligible(commonName, bins, it.home, it.visited) }
        return Tally(eligible = eligible.size, logged = eligible.count { commonName in it.logged })
    }

    fun decide(matchup: Matchup, upper: Tally, lower: Tally): Decision {
        val upperCounts = upper.eligible >= MIN_ELIGIBLE
        val lowerCounts = lower.eligible >= MIN_ELIGIBLE
        // Shares compared as whole-number cross products: no rounding can turn a tie into a win.
        val upperScore = if (upperCounts) upper.logged.toLong() * lower.eligible.coerceAtLeast(1) else 0L
        val lowerScore = if (lowerCounts) lower.logged.toLong() * upper.eligible.coerceAtLeast(1) else 0L
        val betterSeedIsUpper = isBetterSeed(matchup.upper, matchup.lower)
        return when {
            upperScore > lowerScore -> Decision(matchup.upper, matchup.lower, Decision.Reason.HIGHER_SHARE)
            lowerScore > upperScore -> Decision(matchup.lower, matchup.upper, Decision.Reason.HIGHER_SHARE)
            else -> {
                val reason = if (upperCounts || lowerCounts) Decision.Reason.TIED_BETTER_SEED else Decision.Reason.TOO_FEW_ELIGIBLE
                if (betterSeedIsUpper) Decision(matchup.upper, matchup.lower, reason) else Decision(matchup.lower, matchup.upper, reason)
            }
        }
    }

    /** Lower seed number wins a tie; equal seeds (two quarters' champions) fall back to the name. */
    private fun isBetterSeed(a: Team, b: Team): Boolean =
        compareValuesBy(a, b, { it.seed }, { it.commonName }, { it.quarter }) <= 0
}
