package io.cosgriff.flywaybracket.ui.league

import io.cosgriff.flywaybracket.data.League
import io.cosgriff.flywaybracket.data.LeagueMember
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendarResult
import io.cosgriff.flywaybracket.domain.league.LeaguePhase
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The league and its players are made up.
class LeagueViewTest {
    private val denver = ZoneId.of("America/Denver")

    private fun league(phase: LeaguePhase) = League(
        id = "test-league-1",
        name = "Test League",
        inviteCode = "TESTWREN",
        players = 2,
        capacity = 64,
        phase = phase,
        closedBecause = null,
        timeZone = denver,
        calendar = (SeasonCalendar.of(LocalDate.of(2027, 3, 1)) as SeasonCalendarResult.Valid).calendar,
        members = listOf(
            LeagueMember("Sora Example", points = 5, rightPicks = 5, rank = 1, you = false, memberKey = "a"),
            LeagueMember("Wren Example", points = 3, rightPicks = 3, rank = 2, you = true, memberKey = "b"),
        ),
        fetchedAt = Instant.parse("2027-03-10T16:30:00Z"),
    )

    @Test
    fun `a running league shows its round and decided matchups in its own zone, and its fetch time in the phone's`() {
        // 05:00 UTC on Monday 15 March is still Sunday 14 March in Denver: round 2, only round 1 decided.
        val view = leagueView(league(LeaguePhase.RUNNING), Instant.parse("2027-03-15T05:00:00Z"), ZoneId.of("UTC"))

        assertEquals(Season.SPRING, view.season)
        assertEquals(LocalDate.of(2027, 4, 11), view.seasonEnd)
        assertEquals(Round.ROUND_OF_32, view.round)
        assertEquals(32, view.decidedMatchups)
        assertEquals(StandingRow(rank = 2, name = "Wren Example", points = 3, rightPicks = 3, you = true), view.you)
        assertEquals(LocalDateTime.of(2027, 3, 10, 16, 30), view.fetchedAt)
    }

    @Test
    fun `a forming league has no round, and can be left`() {
        val view = leagueView(league(LeaguePhase.FORMING), Instant.parse("2027-02-20T12:00:00Z"), denver)

        assertNull(view.round)
        assertEquals(0, view.decidedMatchups)
        assertEquals(true, view.canLeave)
        assertEquals(false, leagueView(league(LeaguePhase.FINISHED), Instant.parse("2027-04-20T12:00:00Z"), denver).canLeave)
    }
}
