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
     */
    fun isEligible(commonName: String, bins: List<TimeBin>, home: CountyChart, visited: List<CountyChart> = emptyList()): Boolean =
        (visited + home).any { county -> bins.any { bin -> county.isExpected(commonName, bin) } }

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
