package io.cosgriff.flywaybracket.ui.bracket

import androidx.compose.runtime.Immutable
import io.cosgriff.flywaybracket.domain.bracket.DecidedMatchup
import io.cosgriff.flywaybracket.domain.bracket.Decision
import io.cosgriff.flywaybracket.domain.bracket.DrawPosition
import io.cosgriff.flywaybracket.domain.bracket.MatchupRule
import io.cosgriff.flywaybracket.domain.bracket.MatchupView
import io.cosgriff.flywaybracket.domain.bracket.PickScore
import io.cosgriff.flywaybracket.domain.bracket.PickStatus
import io.cosgriff.flywaybracket.domain.bracket.PlayerPart
import io.cosgriff.flywaybracket.domain.bracket.PlayerRound
import io.cosgriff.flywaybracket.domain.bracket.Side
import io.cosgriff.flywaybracket.domain.bracket.Tally
import io.cosgriff.flywaybracket.domain.bracket.Team
import io.cosgriff.flywaybracket.domain.pack.TimeBin
import java.time.LocalDate

/**
 * One matchup as its page shows it (#42).
 *
 * @property quarter the quarter it is played in; null for a semifinal or the final
 * @property dates the round's Monday through its Sunday, in the league's zone
 * @property live whether its round is being played
 * @property result how it was decided; null until then
 */
@Immutable
data class MatchupState(
    val at: DrawPosition,
    val quarter: String?,
    val upper: MatchupSide,
    val lower: MatchupSide,
    val pick: Team?,
    val pickStatus: PickStatus,
    val dates: ClosedRange<LocalDate>,
    val live: Boolean,
    val result: MatchupOutcome?,
) {
    /**
     * What the pick earns if it wins: the round's points, and the seed gap when it is the lower seed; null with
     * no pick, once decided, or while the other bird is not known.
     */
    val ifPickWins: PickScore?
        get() {
            val pick = pick ?: return null
            if (result != null || pickStatus != PickStatus.ALIVE) return null
            val other = listOf(upper, lower).mapNotNull { it.team }.singleOrNull { it != pick } ?: return null
            return PickScore.of(at.round, pick.commonName, Decision(pick, other, Decision.Reason.HIGHER_SHARE))
        }
}

/**
 * One bird of a matchup.
 *
 * @property side who it is ([Side]); the bird is not known yet for [Side.WinnerOf]
 * @property tally of the league's eligible players, how many logged it; null until the matchup is decided
 * @property part whether the player was eligible for it and logged it; null before the round starts
 */
@Immutable
data class MatchupSide(val side: Side, val tally: Tally? = null, val part: PlayerPart? = null) {
    val team: Team?
        get() = when (side) {
            is Side.Known -> side.team
            is Side.YourPick -> side.team
            is Side.WinnerOf -> null
        }
}

/** How a matchup was decided, as its page tells it. */
@Immutable
data class MatchupOutcome(val winner: Team, val loser: Team, val reason: Decision.Reason, val score: PickScore) {
    val isUpset: Boolean get() = winner.seed > loser.seed
}

/**
 * The page's state for [matchup].
 *
 * @param bins the round's bins (`SeasonCalendar.bins`)
 * @param player the player's round as the phone holds it; null before the round starts
 */
internal fun matchupState(
    matchup: MatchupView,
    quarter: String?,
    dates: ClosedRange<LocalDate>,
    live: Boolean,
    bins: List<TimeBin>,
    player: PlayerRound?,
): MatchupState {
    val result = matchup.result
    fun side(side: Side, tally: Tally?): MatchupSide {
        val name = when (side) {
            is Side.Known -> side.team.commonName
            else -> null
        }
        return MatchupSide(side, tally, part = if (player != null && name != null) MatchupRule.part(name, bins, player) else null)
    }
    return MatchupState(
        at = matchup.at,
        quarter = quarter,
        upper = side(matchup.upper, result?.upper),
        lower = side(matchup.lower, result?.lower),
        pick = matchup.pick,
        pickStatus = matchup.pickStatus,
        dates = dates,
        live = live,
        result = outcome(matchup, result),
    )
}

private fun outcome(matchup: MatchupView, result: DecidedMatchup?): MatchupOutcome? {
    val winner = matchup.winner ?: return null
    result ?: return null
    val loser = listOf(matchup.upper, matchup.lower).filterIsInstance<Side.Known>().map { it.team }.firstOrNull { it != winner } ?: return null
    return MatchupOutcome(winner, loser, result.reason, result.score)
}
