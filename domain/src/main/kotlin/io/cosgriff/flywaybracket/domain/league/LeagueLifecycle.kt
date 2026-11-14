package io.cosgriff.flywaybracket.domain.league

import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendarResult
import java.time.LocalDate

/** Where a league is in its life (#32). */
enum class LeaguePhase {
    /** The season has not started. Players can join and leave. */
    FORMING,

    /** The season has started with enough players. No one can join. */
    RUNNING,

    /** The final's Sunday has ended. */
    FINISHED,

    /** The season never started: too few players, even after every postponement. */
    CLOSED,
}

/**
 * A league's phase and its season's start, as last settled.
 *
 * @property seasonStart the first Monday; later than the date the league was created with when the start
 *   has been postponed.
 * @property postponements how many times the start has moved back a week.
 */
data class LeagueStatus(val phase: LeaguePhase, val seasonStart: LocalDate, val postponements: Int = 0) {
    init {
        require(SeasonCalendar.of(seasonStart) is SeasonCalendarResult.Valid) { "A season starts on a Monday" }
        require(postponements in 0..LeagueLifecycle.MAX_POSTPONEMENTS) { "postponements out of range" }
    }

    /** The calendar of the season as it now starts. */
    val calendar: SeasonCalendar get() = (SeasonCalendar.of(seasonStart) as SeasonCalendarResult.Valid).calendar

    /** The Sunday of the final. */
    val seasonEnd: LocalDate get() = calendar.roundDates(Round.FINAL).endInclusive

    /** Whether a player in this league is in a league now: one that is forming or running. */
    val isActive: Boolean get() = phase == LeaguePhase.FORMING || phase == LeaguePhase.RUNNING
}

/** Whether a player can join a league now. */
enum class JoinCheck {
    CAN_JOIN,

    /** The league has [LeagueLifecycle.MAX_PLAYERS] players. */
    FULL,

    /** The season has started, or the league is finished or closed. */
    NOT_FORMING,
}

/**
 * How many players a league holds, and what happens when its start date arrives (#32). Every date is in
 * the league's time zone.
 */
object LeagueLifecycle {
    /** The most players a league holds. */
    const val MAX_PLAYERS = 64

    /** The fewest players a bracket needs when the season starts. */
    const val MIN_PLAYERS = 8

    /** How many times a start moves back a week for want of players before the league is closed. */
    const val MAX_POSTPONEMENTS = 4

    /**
     * The status of a league on [today], from its last settled [status] and the [players] in it.
     *
     * When the start date arrives (its Monday has begun) with at least [MIN_PLAYERS] players, the season
     * runs. With fewer, the start moves back one week, up to [MAX_POSTPONEMENTS] times; after that the
     * league is closed. A running league is finished once the final's Sunday has ended.
     *
     * Several of these steps are taken at once when several dates have passed. That is right only when
     * [players] has not changed since those dates: the caller settles a league before any change to its
     * players.
     */
    fun settle(status: LeagueStatus, players: Int, today: LocalDate): LeagueStatus {
        var current = status
        while (true) {
            current = next(current, players, today) ?: return current
        }
    }

    /** Whether a player can join a league whose [status] has been settled for today. */
    fun joinCheck(status: LeagueStatus, players: Int): JoinCheck = when {
        status.phase != LeaguePhase.FORMING -> JoinCheck.NOT_FORMING
        players >= MAX_PLAYERS -> JoinCheck.FULL
        else -> JoinCheck.CAN_JOIN
    }

    /** The one step [settle] takes next, or null when there is none to take on [today]. */
    private fun next(status: LeagueStatus, players: Int, today: LocalDate): LeagueStatus? = when (status.phase) {
        LeaguePhase.FORMING -> when {
            today < status.seasonStart -> null
            players >= MIN_PLAYERS -> status.copy(phase = LeaguePhase.RUNNING)
            status.postponements < MAX_POSTPONEMENTS ->
                status.copy(seasonStart = status.seasonStart.plusWeeks(1), postponements = status.postponements + 1)
            else -> status.copy(phase = LeaguePhase.CLOSED)
        }
        LeaguePhase.RUNNING -> if (today > status.seasonEnd) status.copy(phase = LeaguePhase.FINISHED) else null
        LeaguePhase.FINISHED, LeaguePhase.CLOSED -> null
    }
}
