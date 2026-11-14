package io.cosgriff.flywaybracket.domain.league

import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendarResult
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * The rules for the start date a new league is given (#32). Every date is in the league's time zone: the
 * caller turns "now" into the league's date.
 */
object SeasonStart {
    /** A new league's season starts at least this many days after the day it is created. */
    const val MIN_DAYS_AHEAD = 7L

    /** Whether [start] can start the season of a league created on [today]. */
    fun check(start: LocalDate, today: LocalDate): SeasonStartCheck = when {
        SeasonCalendar.of(start) is SeasonCalendarResult.NotAMonday -> SeasonStartCheck.NOT_A_MONDAY
        start < today.plusDays(MIN_DAYS_AHEAD) -> SeasonStartCheck.TOO_SOON
        else -> SeasonStartCheck.VALID
    }

    /**
     * The start of a league the server opens for a player who asked to be put in one and found none with
     * room: the second Monday after [today]. That is 8 to 14 days ahead, so it always passes [check].
     */
    fun forNewOpenLeague(today: LocalDate): LocalDate = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).plusWeeks(1)
}

/** What [SeasonStart.check] found. Only [VALID] may start a season. */
enum class SeasonStartCheck { NOT_A_MONDAY, TOO_SOON, VALID }
