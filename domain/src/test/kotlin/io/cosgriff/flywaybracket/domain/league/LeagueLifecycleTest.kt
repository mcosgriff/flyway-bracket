package io.cosgriff.flywaybracket.domain.league

import io.cosgriff.flywaybracket.domain.bracket.Field
import io.cosgriff.flywaybracket.domain.bracket.FieldResult
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendarResult
import io.cosgriff.flywaybracket.domain.league.LeagueLifecycle.MAX_PLAYERS
import io.cosgriff.flywaybracket.domain.league.LeagueLifecycle.MIN_PLAYERS
import io.cosgriff.flywaybracket.domain.league.LeagueLifecycle.joinCheck
import io.cosgriff.flywaybracket.domain.pack.migrantChart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LeagueLifecycleTest {
    /** Monday 1 March 2027; the final is 5 to 11 April. */
    private val start = LocalDate.of(2027, 3, 1)
    private val forming = LeagueStatus(LeaguePhase.FORMING, start)

    /** A full field of made-up migrants, whatever the start. */
    private val fullField: FieldResult = Field.choose(listOf(migrantChart("XX-FD-001")), calendar(start).allBins())

    private fun calendar(start: LocalDate) = (SeasonCalendar.of(start) as SeasonCalendarResult.Valid).calendar

    /** Settles with a full field, so only the players decide, at [time] on [today]. */
    private fun settle(status: LeagueStatus, players: Int, today: LocalDate, time: LocalTime = LocalTime.NOON) =
        LeagueLifecycle.settle(status, players, today.atTime(time)) { fullField }

    @Test
    fun `a league holds 64 players and its bracket needs 8`() {
        assertEquals(64, MAX_PLAYERS)
        assertEquals(8, MIN_PLAYERS)
        assertEquals(4, LeagueLifecycle.MAX_POSTPONEMENTS)
    }

    // Before the start

    @Test
    fun `nothing changes before the start date`() {
        assertEquals(forming, settle(forming, players = 3, today = start.minusDays(1)))
        assertEquals(forming, settle(forming, players = 20, today = start.minusDays(1)))
    }

    // The start date arrives

    @Test
    fun `with 8 players the season starts on its date`() {
        assertEquals(LeagueStatus(LeaguePhase.RUNNING, start), settle(forming, players = MIN_PLAYERS, today = start))
        assertEquals(LeagueStatus(LeaguePhase.RUNNING, start), settle(forming, players = MAX_PLAYERS, today = start))
    }

    @Test
    fun `with 7 players the start moves back one week`() {
        assertEquals(
            LeagueStatus(LeaguePhase.FORMING, LocalDate.of(2027, 3, 8), postponements = 1),
            settle(forming, players = MIN_PLAYERS - 1, today = start),
        )
    }

    @Test
    fun `a postponed league starts when it has 8 players by its new date`() {
        val postponed = LeagueStatus(LeaguePhase.FORMING, LocalDate.of(2027, 3, 8), postponements = 1)

        assertEquals(postponed, settle(postponed, players = MIN_PLAYERS, today = LocalDate.of(2027, 3, 7)))
        assertEquals(
            LeagueStatus(LeaguePhase.RUNNING, LocalDate.of(2027, 3, 8), postponements = 1),
            settle(postponed, players = MIN_PLAYERS, today = LocalDate.of(2027, 3, 8)),
        )
    }

    @Test
    fun `the start moves back up to four times`() {
        val fourth = LeagueStatus(LeaguePhase.FORMING, LocalDate.of(2027, 3, 29), postponements = 4)

        assertEquals(fourth, settle(forming, players = 1, today = LocalDate.of(2027, 3, 22)))
        assertEquals(fourth, settle(forming, players = 1, today = LocalDate.of(2027, 3, 28)))
    }

    @Test
    fun `then the league is closed`() {
        val closed = LeagueStatus(LeaguePhase.CLOSED, LocalDate.of(2027, 3, 29), postponements = 4, CloseReason.TOO_FEW_PLAYERS)

        assertEquals(closed, settle(forming, players = MIN_PLAYERS - 1, today = LocalDate.of(2027, 3, 29)))
        assertEquals(closed, settle(forming, players = 0, today = LocalDate.of(2028, 1, 1)))
        assertFalse(closed.isActive)
    }

    @Test
    fun `a closed league stays closed whoever joins`() {
        val closed = LeagueStatus(LeaguePhase.CLOSED, LocalDate.of(2027, 3, 29), postponements = 4, CloseReason.TOO_FEW_PLAYERS)

        assertEquals(closed, settle(closed, players = MAX_PLAYERS, today = LocalDate.of(2027, 4, 5)))
    }

    // The field

    @Test
    fun `with too few birds for a field the league is closed on its start date`() {
        val asked = mutableListOf<LocalDate>()
        val settled = LeagueLifecycle.settle(forming, players = MIN_PLAYERS, now = start.atStartOfDay()) {
            asked += it
            FieldResult.TooFewBirds(candidates = 63)
        }

        assertEquals(LeagueStatus(LeaguePhase.CLOSED, start, closedBecause = CloseReason.TOO_FEW_BIRDS), settled)
        assertEquals(listOf(start), asked)
    }

    @Test
    fun `the field is asked for only when a start arrives with enough players`() {
        val asked = mutableListOf<LocalDate>()
        val field = { start: LocalDate -> fullField.also { asked += start } }

        LeagueLifecycle.settle(forming, players = 20, now = start.minusDays(1).atStartOfDay(), field)
        LeagueLifecycle.settle(forming, players = MIN_PLAYERS - 1, now = start.atStartOfDay(), field)
        assertEquals(emptyList<LocalDate>(), asked)

        // Postponed once, then started: the field asked for is the one of the new start.
        val postponed = LeagueStatus(LeaguePhase.FORMING, LocalDate.of(2027, 3, 8), postponements = 1)
        LeagueLifecycle.settle(postponed, players = MIN_PLAYERS, now = LocalDate.of(2027, 6, 1).atStartOfDay(), field)
        assertEquals(listOf(LocalDate.of(2027, 3, 8)), asked)
    }

    @Test
    fun `only a closed league has a reason`() {
        assertNull(forming.closedBecause)
        assertThrows(IllegalArgumentException::class.java) { forming.copy(closedBecause = CloseReason.TOO_FEW_BIRDS) }
        assertThrows(IllegalArgumentException::class.java) { forming.copy(phase = LeaguePhase.CLOSED) }
    }

    // The season ends

    @Test
    fun `a running league is finished once the final is decided, 12 hours after its Sunday ends`() {
        val running = LeagueStatus(LeaguePhase.RUNNING, start)
        val mondayAfter = LocalDate.of(2027, 4, 12)

        assertEquals(LocalDate.of(2027, 4, 11), running.seasonEnd)
        assertEquals(running, settle(running, players = 8, today = LocalDate.of(2027, 4, 11), time = LocalTime.MAX))
        assertEquals(running, settle(running, players = 8, today = mondayAfter, time = LocalTime.of(11, 59, 59)))
        assertEquals(LeagueStatus(LeaguePhase.FINISHED, start), settle(running, players = 8, today = mondayAfter, time = LocalTime.NOON))
        assertEquals(
            LeagueStatus(LeaguePhase.FINISHED, start),
            LeagueLifecycle.settle(running, players = 8, LocalDateTime.of(2027, 4, 20, 0, 0)) { fullField },
        )
    }

    @Test
    fun `several dates passed at once are all settled`() {
        assertEquals(LeagueStatus(LeaguePhase.FINISHED, start), settle(forming, players = 9, today = LocalDate.of(2027, 6, 1)))
        assertEquals(
            LeagueStatus(LeaguePhase.FINISHED, LocalDate.of(2027, 3, 8), postponements = 1),
            settle(LeagueStatus(LeaguePhase.FORMING, LocalDate.of(2027, 3, 8), 1), players = 8, today = LocalDate.of(2027, 6, 1)),
        )
    }

    @Test
    fun `forming and running leagues are active, finished and closed ones are not`() {
        assertTrue(forming.isActive)
        assertTrue(forming.copy(phase = LeaguePhase.RUNNING).isActive)
        assertFalse(forming.copy(phase = LeaguePhase.FINISHED).isActive)
        assertFalse(forming.copy(phase = LeaguePhase.CLOSED, closedBecause = CloseReason.TOO_FEW_PLAYERS).isActive)
    }

    // Joining

    @Test
    fun `a forming league with room can be joined`() {
        assertEquals(JoinCheck.CAN_JOIN, joinCheck(forming, players = 0))
        assertEquals(JoinCheck.CAN_JOIN, joinCheck(forming, players = MAX_PLAYERS - 1))
    }

    @Test
    fun `the 65th player is refused`() {
        assertEquals(JoinCheck.FULL, joinCheck(forming, players = MAX_PLAYERS))
    }

    @Test
    fun `joining closes when the season starts`() {
        for (phase in listOf(LeaguePhase.RUNNING, LeaguePhase.FINISHED)) {
            assertEquals(JoinCheck.NOT_FORMING, joinCheck(forming.copy(phase = phase), players = 9))
        }
        val closed = forming.copy(phase = LeaguePhase.CLOSED, closedBecause = CloseReason.TOO_FEW_BIRDS)
        assertEquals(JoinCheck.NOT_FORMING, joinCheck(closed, players = 9))
    }

    // The status itself

    @Test
    fun `a status starts on a Monday and is postponed at most four times`() {
        assertThrows(IllegalArgumentException::class.java) { LeagueStatus(LeaguePhase.FORMING, start.plusDays(1)) }
        assertThrows(IllegalArgumentException::class.java) { LeagueStatus(LeaguePhase.FORMING, start, postponements = 5) }
        assertThrows(IllegalArgumentException::class.java) { LeagueStatus(LeaguePhase.FORMING, start, postponements = -1) }
    }

    @Test
    fun `a postponed start keeps the six-week calendar from its new date`() {
        val postponed = LeagueStatus(LeaguePhase.FORMING, LocalDate.of(2027, 3, 8), postponements = 1)

        assertEquals(LocalDate.of(2027, 3, 8), postponed.calendar.startDate)
        assertEquals(LocalDate.of(2027, 4, 18), postponed.seasonEnd)
    }
}
