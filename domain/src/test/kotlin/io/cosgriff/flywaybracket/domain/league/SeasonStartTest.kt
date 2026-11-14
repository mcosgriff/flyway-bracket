package io.cosgriff.flywaybracket.domain.league

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class SeasonStartTest {
    /** Wednesday 17 February 2027. */
    private val wednesday = LocalDate.of(2027, 2, 17)

    @Test
    fun `a Monday at least 7 days ahead can start a season`() {
        assertEquals(SeasonStartCheck.VALID, SeasonStart.check(LocalDate.of(2027, 3, 1), today = wednesday))
        assertEquals(SeasonStartCheck.VALID, SeasonStart.check(LocalDate.of(2027, 9, 6), today = wednesday))
    }

    @Test
    fun `exactly 7 days ahead is enough`() {
        val monday = LocalDate.of(2027, 2, 22)

        assertEquals(SeasonStartCheck.VALID, SeasonStart.check(LocalDate.of(2027, 3, 1), today = monday))
    }

    @Test
    fun `a Monday less than 7 days ahead is too soon`() {
        assertEquals(SeasonStartCheck.TOO_SOON, SeasonStart.check(LocalDate.of(2027, 2, 22), today = wednesday))
        assertEquals(SeasonStartCheck.TOO_SOON, SeasonStart.check(LocalDate.of(2027, 2, 22), today = LocalDate.of(2027, 2, 22)))
        assertEquals(SeasonStartCheck.TOO_SOON, SeasonStart.check(LocalDate.of(2027, 2, 15), today = wednesday))
    }

    @Test
    fun `any other day is not a Monday, however far ahead`() {
        assertEquals(SeasonStartCheck.NOT_A_MONDAY, SeasonStart.check(LocalDate.of(2027, 3, 2), today = wednesday))
        assertEquals(SeasonStartCheck.NOT_A_MONDAY, SeasonStart.check(wednesday, today = wednesday))
    }

    @Test
    fun `a new open league starts on the second Monday from today`() {
        assertEquals(LocalDate.of(2027, 3, 1), SeasonStart.forNewOpenLeague(wednesday))
        // On a Sunday the next Monday is tomorrow; the second is 8 days ahead.
        assertEquals(LocalDate.of(2027, 3, 1), SeasonStart.forNewOpenLeague(LocalDate.of(2027, 2, 21)))
        // On a Monday, today does not count.
        assertEquals(LocalDate.of(2027, 3, 8), SeasonStart.forNewOpenLeague(LocalDate.of(2027, 2, 22)))
    }

    @Test
    fun `a new open league's start always passes the check`() {
        for (day in 0L..13L) {
            val today = wednesday.plusDays(day)
            val start = SeasonStart.forNewOpenLeague(today)
            assertEquals(DayOfWeek.MONDAY, start.dayOfWeek)
            assertEquals(today.toString(), SeasonStartCheck.VALID, SeasonStart.check(start, today))
        }
    }
}
