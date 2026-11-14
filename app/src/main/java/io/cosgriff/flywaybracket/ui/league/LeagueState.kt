package io.cosgriff.flywaybracket.ui.league

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.cosgriff.flywaybracket.data.League
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.league.CloseReason
import io.cosgriff.flywaybracket.domain.league.LeaguePhase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** What the League tab shows (#40). */
@Immutable
sealed interface LeagueTabState {
    /** The player's league is being read, and the phone holds none. */
    data object Loading : LeagueTabState

    /** The league could not be read, and the phone holds none. */
    data class Failed(@StringRes val message: Int) : LeagueTabState

    /** The player is in no league, or chose to join another after a finished or closed one. */
    data class Join(val join: JoinState) : LeagueTabState

    data class InLeague(val league: LeagueView) : LeagueTabState
}

/** What is being sent from the Join view. */
enum class JoinBusy { JOINING, FINDING }

/**
 * The Join view: an invite code, Create a league and Find me a league.
 *
 * @property code the invite code as typed
 * @property problem why the last join failed; null when it did not
 * @property canGoBack the player came from a finished or closed league, which Back returns to
 */
@Immutable
data class JoinState(
    val code: String = "",
    val busy: JoinBusy? = null,
    @StringRes val problem: Int? = null,
    val canGoBack: Boolean = false,
)

/** One row of the league's table. [you] marks the player's own row, in words as well as colour. */
@Immutable
data class StandingRow(val rank: Int, val name: String, val points: Int, val rightPicks: Int, val you: Boolean)

/**
 * The player's league as the tab shows it.
 *
 * @property season null, with the dates, when only the league's summary could be read
 * @property seasonStart the first Monday, in the league's zone
 * @property seasonEnd the Sunday of the final, in the league's zone
 * @property timeZone the league's zone id, which its dates are in
 * @property round the round being played; null outside the season
 * @property inviteCode shown while the league forms
 * @property decidedMatchups how many matchups are decided so far, which right picks are out of
 * @property fetchedAt when the phone last read the league, in the phone's zone
 * @property problem why the last update or leave failed; null when it did not
 * @property confirmLeave Leave league was chosen, and the tab asks first
 */
@Immutable
data class LeagueView(
    val name: String,
    val phase: LeaguePhase,
    val closedBecause: CloseReason? = null,
    val season: Season? = null,
    val seasonStart: LocalDate? = null,
    val seasonEnd: LocalDate? = null,
    val timeZone: String? = null,
    val round: Round? = null,
    val players: Int,
    val capacity: Int,
    val inviteCode: String? = null,
    val standings: List<StandingRow> = emptyList(),
    val decidedMatchups: Int = 0,
    val fetchedAt: LocalDateTime,
    @StringRes val problem: Int? = null,
    val confirmLeave: Boolean = false,
    val leaving: Boolean = false,
) {
    val you: StandingRow? get() = standings.firstOrNull { it.you }

    /** A player can leave a league that forms or runs; a finished or closed one leaves them free already. */
    val canLeave: Boolean get() = phase == LeaguePhase.FORMING || phase == LeaguePhase.RUNNING
}

/** What the tab shows of [league] at [now]: its round and decided matchups in its own zone, its fetch time in [phoneZone]. */
internal fun leagueView(league: League, now: Instant, phoneZone: ZoneId): LeagueView {
    val calendar = league.calendar
    val zone = league.timeZone
    val leagueNow = zone?.let { now.atZone(it).toLocalDateTime() }
    return LeagueView(
        name = league.name,
        phase = league.phase,
        closedBecause = league.closedBecause,
        season = calendar?.season,
        seasonStart = calendar?.startDate,
        seasonEnd = calendar?.roundDates(Round.FINAL)?.endInclusive,
        timeZone = zone?.id,
        round = if (league.phase == LeaguePhase.RUNNING && calendar != null && leagueNow != null) calendar.roundOfSeason(leagueNow.toLocalDate()) else null,
        players = league.players,
        capacity = league.capacity,
        inviteCode = league.inviteCode,
        standings = league.members.map { StandingRow(it.rank, it.displayName, it.points, it.rightPicks, it.you) },
        decidedMatchups = if (calendar != null && leagueNow != null) calendar.decidedMatchups(leagueNow) else 0,
        fetchedAt = league.fetchedAt.atZone(phoneZone).toLocalDateTime(),
    )
}
