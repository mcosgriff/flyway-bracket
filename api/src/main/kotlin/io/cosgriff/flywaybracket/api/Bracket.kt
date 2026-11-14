package io.cosgriff.flywaybracket.api

import io.cosgriff.flywaybracket.domain.bracket.Decision
import io.cosgriff.flywaybracket.domain.bracket.Round
import kotlinx.serialization.Serializable

/** A bird in the bracket: `Team` in `:domain`, as it travels. */
@Serializable
data class BracketTeam(val commonName: String, val quarter: String, val seed: Int)

/**
 * One matchup of the bracket. Its round is [slot]'s round.
 *
 * @property upper the common name of the bird drawn above; null until the matchup that feeds it is
 *   decided.
 * @property lower the same, for the bird drawn below.
 * @property winner the common name of the bird that advanced; null until the round is decided.
 */
@Serializable
data class BracketMatchup(
    val slot: MatchupSlot,
    val upper: String? = null,
    val lower: String? = null,
    val winner: String? = null,
)

/**
 * A league's bracket and its results so far.
 *
 * @property teams the 64 birds.
 * @property matchups all 63, in slot order.
 */
@Serializable
data class BracketResponse(val teams: List<BracketTeam>, val matchups: List<BracketMatchup>)

/**
 * The player's whole bracket: for each of the 63 slots, the common name of the bird picked to win it.
 */
@Serializable
data class SubmitPicksRequest(val picks: Map<MatchupSlot, String>) {
    /** Whether every slot has a pick. */
    val isComplete: Boolean get() = picks.size == MatchupSlot.COUNT
}

/**
 * The player's picks as the server holds them; empty before the first submission.
 *
 * @property locked whether the season has started, so the picks can no longer change.
 */
@Serializable
data class PicksResponse(val picks: Map<MatchupSlot, String>, val locked: Boolean)

/** One bird's week in the league: `Tally` in `:domain`, with the bird it is for. */
@Serializable
data class BirdTally(val commonName: String, val eligible: Int, val logged: Int)

/**
 * How one matchup was decided, and what the asking player earned on it.
 *
 * @property winner the common name of [upper]'s or [lower]'s bird.
 * @property roundPoints the round's points when the player picked the winner, else 0.
 * @property upsetBonus the seed gap when the player called an upset, else 0.
 */
@Serializable
data class MatchupResult(
    val slot: MatchupSlot,
    val upper: BirdTally,
    val lower: BirdTally,
    val winner: String,
    val reason: Decision.Reason,
    val roundPoints: Int,
    val upsetBonus: Int,
)

/** Every matchup of a decided round, in slot order. */
@Serializable
data class RoundResultsResponse(val round: Round, val matchups: List<MatchupResult>)

/** A league's table, best rank first. */
@Serializable
data class StandingsResponse(val standings: List<Standing>)
