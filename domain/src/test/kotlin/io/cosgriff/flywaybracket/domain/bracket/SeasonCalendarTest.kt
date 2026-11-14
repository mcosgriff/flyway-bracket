package io.cosgriff.flywaybracket.domain.bracket

import io.cosgriff.flywaybracket.domain.pack.TimeBin
import io.cosgriff.flywaybracket.domain.pack.chart
import io.cosgriff.flywaybracket.domain.pack.seasonal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class SeasonCalendarTest {
    private fun calendar(startDate: LocalDate) = (SeasonCalendar.of(startDate) as SeasonCalendarResult.Valid).calendar

    /** Monday 1 March 2027 to Sunday 11 April 2027. */
    private val spring = calendar(LocalDate.of(2027, 3, 1))

    /** Monday 29 November 2027 to Sunday 9 January 2028. */
    private val acrossNewYear = calendar(LocalDate.of(2027, 11, 29))

    // Start date

    @Test
    fun `a season starts on a Monday`() {
        val monday = LocalDate.of(2027, 3, 1)
        assertEquals(monday, (SeasonCalendar.of(monday) as SeasonCalendarResult.Valid).calendar.startDate)
    }

    @Test
    fun `any other day is reported, with the day it was`() {
        val wednesday = LocalDate.of(2027, 3, 3)
        val result = SeasonCalendar.of(wednesday)
        assertEquals(SeasonCalendarResult.NotAMonday(wednesday), result)
        assertEquals(DayOfWeek.WEDNESDAY, (result as SeasonCalendarResult.NotAMonday).dayOfWeek)
        for (day in 1L..6L) {
            assertTrue(SeasonCalendar.of(LocalDate.of(2027, 3, 1).plusDays(day)) is SeasonCalendarResult.NotAMonday)
        }
    }

    @Test
    fun `two calendars with the same start date are equal`() {
        assertEquals(SeasonCalendar.of(LocalDate.of(2027, 3, 1)), SeasonCalendar.of(LocalDate.of(2027, 3, 1)))
        assertEquals(spring.hashCode(), calendar(LocalDate.of(2027, 3, 1)).hashCode())
        assertFalse(spring == acrossNewYear)
        assertEquals("SeasonCalendar(startDate=2027-03-01)", spring.toString())
    }

    // Rounds

    @Test
    fun `the six rounds are six consecutive weeks, Monday through Sunday`() {
        assertEquals(LocalDate.of(2027, 3, 1)..LocalDate.of(2027, 3, 7), spring.roundDates(Round.ROUND_OF_64))
        assertEquals(LocalDate.of(2027, 3, 8)..LocalDate.of(2027, 3, 14), spring.roundDates(Round.ROUND_OF_32))
        assertEquals(LocalDate.of(2027, 3, 15)..LocalDate.of(2027, 3, 21), spring.roundDates(Round.ROUND_OF_16))
        assertEquals(LocalDate.of(2027, 3, 22)..LocalDate.of(2027, 3, 28), spring.roundDates(Round.QUARTERFINAL))
        assertEquals(LocalDate.of(2027, 3, 29)..LocalDate.of(2027, 4, 4), spring.roundDates(Round.SEMIFINAL))
        assertEquals(LocalDate.of(2027, 4, 5)..LocalDate.of(2027, 4, 11), spring.roundDates(Round.FINAL))
        for (round in Round.entries) {
            assertEquals(DayOfWeek.MONDAY, spring.roundDates(round).start.dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, spring.roundDates(round).endInclusive.dayOfWeek)
        }
    }

    @Test
    fun `a date is in the round whose week holds it`() {
        assertEquals(Round.ROUND_OF_64, spring.roundOn(LocalDate.of(2027, 3, 1)))
        assertEquals(Round.ROUND_OF_64, spring.roundOn(LocalDate.of(2027, 3, 7)))
        assertEquals(Round.ROUND_OF_32, spring.roundOn(LocalDate.of(2027, 3, 8)))
        assertEquals(Round.SEMIFINAL, spring.roundOn(LocalDate.of(2027, 4, 1)))
        assertEquals(Round.FINAL, spring.roundOn(LocalDate.of(2027, 4, 11)))
    }

    @Test
    fun `there is no round the day before the season or the day after the final`() {
        assertNull(spring.roundOn(LocalDate.of(2027, 2, 28)))
        assertNull(spring.roundOn(LocalDate.of(2027, 4, 12)))
    }

    @Test
    fun `picks lock on the first Monday`() {
        assertEquals(LocalDate.of(2027, 3, 1), spring.picksLockOn)
        assertEquals(spring.roundDates(Round.ROUND_OF_64).start, spring.picksLockOn)
    }

    @Test
    fun `the field is fixed 48 hours before picks lock, on the Saturday`() {
        assertEquals(LocalDate.of(2027, 2, 27), spring.fieldFixedOn)
        assertEquals(java.time.DayOfWeek.SATURDAY, spring.fieldFixedOn.dayOfWeek)
        assertEquals(false, spring.isFieldFixed(LocalDate.of(2027, 2, 26)))
        assertEquals(true, spring.isFieldFixed(LocalDate.of(2027, 2, 27)))
        assertEquals(true, spring.isFieldFixed(LocalDate.of(2027, 3, 1)))
    }

    // Season

    @Test
    fun `a season starting in January through June is spring, else fall`() {
        assertEquals(Season.SPRING, spring.season)
        assertEquals(Season.FALL, acrossNewYear.season)
        assertEquals(Season.SPRING, calendar(LocalDate.of(2027, 1, 4)).season)
        assertEquals(Season.SPRING, calendar(LocalDate.of(2027, 6, 28)).season)
        assertEquals(Season.FALL, calendar(LocalDate.of(2027, 7, 5)).season)
        assertEquals(Season.FALL, calendar(LocalDate.of(2027, 12, 27)).season)
    }

    // Bins

    @Test
    fun `a round inside one bin covers that bin`() {
        // 1 to 7 March is exactly March's first part.
        assertEquals(listOf(TimeBin.of(3, 1)), spring.bins(Round.ROUND_OF_64))
    }

    @Test
    fun `a round across two bins covers both, earlier first`() {
        // 5 to 11 April: three days in the first part, four in the second.
        assertEquals(listOf(TimeBin.of(4, 1), TimeBin.of(4, 2)), spring.bins(Round.FINAL))
    }

    @Test
    fun `a round across a month end covers the old month's last bin and the new month's first`() {
        // 29 March to 4 April.
        assertEquals(listOf(TimeBin.of(3, 4), TimeBin.of(4, 1)), spring.bins(Round.SEMIFINAL))
    }

    @Test
    fun `a round across the year end puts December before January`() {
        // 27 December 2027 to 2 January 2028.
        assertEquals(LocalDate.of(2027, 12, 27)..LocalDate.of(2028, 1, 2), acrossNewYear.roundDates(Round.SEMIFINAL))
        assertEquals(listOf(TimeBin.of(12, 4), TimeBin.of(1, 1)), acrossNewYear.bins(Round.SEMIFINAL))
        assertEquals(Round.SEMIFINAL, acrossNewYear.roundOn(LocalDate.of(2028, 1, 1)))
    }

    @Test
    fun `a season's bins are every round's, in order, each once`() {
        assertEquals(
            listOf(TimeBin.of(3, 1), TimeBin.of(3, 2), TimeBin.of(3, 3), TimeBin.of(3, 4), TimeBin.of(4, 1), TimeBin.of(4, 2)),
            spring.allBins(),
        )
    }

    @Test
    fun `a season across the year end keeps its bins in date order`() {
        assertEquals(
            listOf(
                TimeBin.of(11, 4),
                TimeBin.of(12, 1),
                TimeBin.of(12, 2),
                TimeBin.of(12, 3),
                TimeBin.of(12, 4),
                TimeBin.of(1, 1),
                TimeBin.of(1, 2),
            ),
            acrossNewYear.allBins(),
        )
        assertEquals(LocalDate.of(2028, 1, 9), acrossNewYear.roundDates(Round.FINAL).endInclusive)
        assertNull(acrossNewYear.roundOn(LocalDate.of(2027, 11, 28)))
        assertNull(acrossNewYear.roundOn(LocalDate.of(2028, 1, 10)))
    }

    // With the rules that take bins

    @Test
    fun `a bird is expected for a round when it is expected in any bin the round covers`() {
        // Arrives in April's second part: the final (5 to 11 April) touches it, the semifinal doesn't.
        val home = chart(seasonal("Test Oriole", TimeBin.of(4, 2), TimeBin.of(8, 4), inSeason = 0.20))
        assertTrue(MatchupRule.isEligible("Test Oriole", spring.bins(Round.FINAL), home))
        assertFalse(MatchupRule.isEligible("Test Oriole", spring.bins(Round.SEMIFINAL), home))
    }

    @Test
    fun `seeding looks at every bin the six rounds cover`() {
        // In season only from the last round's second bin on. Its smoothed frequency there is two
        // bins of 0.60 and one of nothing: 0.40.
        val lateArrival = chart(seasonal("Test Hummingbird", TimeBin.of(4, 2), TimeBin.of(9, 4), inSeason = 0.60))
        assertEquals(0.40, Seeding.strength("Test Hummingbird", listOf(lateArrival), spring.allBins()), 1e-9)
        // Gone before the fall season across the year end starts.
        assertEquals(0.0, Seeding.strength("Test Hummingbird", listOf(lateArrival), acrossNewYear.allBins()), 0.0)
    }
}
