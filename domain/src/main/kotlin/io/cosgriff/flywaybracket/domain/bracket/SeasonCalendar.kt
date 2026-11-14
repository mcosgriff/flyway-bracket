package io.cosgriff.flywaybracket.domain.bracket

import io.cosgriff.flywaybracket.domain.pack.TimeBin
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month

/** What a start date gave: a season's calendar, or why the date can't start a season. */
sealed interface SeasonCalendarResult {
    data class Valid(val calendar: SeasonCalendar) : SeasonCalendarResult

    /** A season starts on a Monday; [startDate] is a [dayOfWeek]. */
    data class NotAMonday(val startDate: LocalDate) : SeasonCalendarResult {
        val dayOfWeek: DayOfWeek get() = startDate.dayOfWeek
    }
}

/**
 * When a league's season runs (#20). The six rounds are six consecutive weeks, Monday through
 * Sunday, from [startDate]; a round is decided [DECISION_DELAY_HOURS] hours after its Sunday ends
 * ([decidedAt]). Every date is in the league's one time zone: the caller turns "now" into the league's
 * date and time.
 *
 * Made with [of], which takes only a Monday.
 */
class SeasonCalendar private constructor(val startDate: LocalDate) {
    /**
     * The day picks for the whole bracket lock, at its start: the first Monday. A player picks all
     * 63 matchups before it.
     */
    val picksLockOn: LocalDate get() = startDate

    /**
     * The day the league's field is fixed, at its start: the Saturday before the season, 48 hours before
     * picks lock (#34). Until then the field is a preview, seeded again whenever the members or their home
     * counties change; from then on a late joiner's county no longer changes it. Joining still closes only
     * when the season starts.
     */
    val fieldFixedOn: LocalDate get() = startDate.minusDays(FIELD_FIXED_DAYS_AHEAD)

    /** Whether the field is fixed on [today]: from [fieldFixedOn] on. */
    fun isFieldFixed(today: LocalDate): Boolean = today >= fieldFixedOn

    /** Spring when the season starts in January through June, else fall. */
    val season: Season get() = if (startDate.month <= Month.JUNE) Season.SPRING else Season.FALL

    /** The round's Monday through its Sunday. */
    fun roundDates(round: Round): ClosedRange<LocalDate> {
        val monday = startDate.plusWeeks(round.ordinal.toLong())
        return monday..monday.plusDays(DAYS_PER_ROUND - 1L)
    }

    /**
     * When [round] is decided (#37): [DECISION_DELAY_HOURS] hours after its Sunday ends, at noon on the
     * Monday after, so that sightings from a phone that was offline over the weekend can still arrive. What
     * arrives later does not change the round.
     */
    fun decidedAt(round: Round): LocalDateTime =
        roundDates(round).endInclusive.plusDays(1).atStartOfDay().plusHours(DECISION_DELAY_HOURS)

    /** Whether [round] is decided at [now]: from [decidedAt] on. */
    fun isDecided(round: Round, now: LocalDateTime): Boolean = now >= decidedAt(round)

    /** The round played on [date]; null before the season and after the final. */
    fun roundOn(date: LocalDate): Round? = Round.entries.firstOrNull { date in roundDates(it) }

    /**
     * Every [TimeBin] one of the round's seven days falls in, earliest first, each once. A bird is
     * expected for the round when it is expected in any of them.
     */
    fun bins(round: Round): List<TimeBin> {
        val monday = roundDates(round).start
        return List(DAYS_PER_ROUND) { day -> TimeBin.of(monday.plusDays(day.toLong())) }.distinct()
    }

    /** Every bin the six rounds cover, earliest first, each once. Seeding looks at these. */
    fun allBins(): List<TimeBin> = Round.entries.flatMap(::bins).distinct()

    override fun equals(other: Any?): Boolean = other is SeasonCalendar && other.startDate == startDate

    override fun hashCode(): Int = startDate.hashCode()

    override fun toString(): String = "SeasonCalendar(startDate=$startDate)"

    companion object {
        const val DAYS_PER_ROUND = 7

        /** A round is decided this many hours after its Sunday ends ([decidedAt]). */
        const val DECISION_DELAY_HOURS = 12L

        /** The field is fixed this many days, 48 hours, before the season starts. */
        const val FIELD_FIXED_DAYS_AHEAD = 2L

        /** The calendar of the season starting on [startDate], or [SeasonCalendarResult.NotAMonday]. */
        fun of(startDate: LocalDate): SeasonCalendarResult =
            if (startDate.dayOfWeek == DayOfWeek.MONDAY) {
                SeasonCalendarResult.Valid(SeasonCalendar(startDate))
            } else {
                SeasonCalendarResult.NotAMonday(startDate)
            }
    }
}
