package io.cosgriff.flywaybracket.domain.league

import io.cosgriff.flywaybracket.domain.league.LeagueLifecycle.MAX_PLAYERS
import io.cosgriff.flywaybracket.domain.league.LeagueLifecycle.MIN_PLAYERS
import io.cosgriff.flywaybracket.domain.league.LeagueLifecycle.joinCheck
import io.cosgriff.flywaybracket.domain.league.LeagueLifecycle.settle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LeagueLifecycleTest {
    /** Monday 1 March 2027; the final is 5 to 11 April. */
    private val start = LocalDate.of(2027, 3, 1)
    private val forming = LeagueStatus(LeaguePhase.FORMING, start)

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
        val closed = LeagueStatus(LeaguePhase.CLOSED, LocalDate.of(2027, 3, 29), postponements = 4)

        assertEquals(closed, settle(forming, players = MIN_PLAYERS - 1, today = LocalDate.of(2027, 3, 29)))
        assertEquals(closed, settle(forming, players = 0, today = LocalDate.of(2028, 1, 1)))
        assertFalse(closed.isActive)
    }

    @Test
    fun `a closed league stays closed whoever joins`() {
        val closed = LeagueStatus(LeaguePhase.CLOSED, LocalDate.of(2027, 3, 29), postponements = 4)

        assertEquals(closed, settle(closed, players = MAX_PLAYERS, today = LocalDate.of(2027, 4, 5)))
    }

    // The season ends

    @Test
    fun `a running league is finished once the final's Sunday has ended`() {
        val running = LeagueStatus(LeaguePhase.RUNNING, start)

        assertEquals(LocalDate.of(2027, 4, 11), running.seasonEnd)
        assertEquals(running, settle(running, players = 8, today = LocalDate.of(2027, 4, 11)))
        assertEquals(LeagueStatus(LeaguePhase.FINISHED, start), settle(running, players = 8, today = LocalDate.of(2027, 4, 12)))
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
        assertFalse(forming.copy(phase = LeaguePhase.CLOSED).isActive)
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
        for (phase in listOf(LeaguePhase.RUNNING, LeaguePhase.FINISHED, LeaguePhase.CLOSED)) {
            assertEquals(JoinCheck.NOT_FORMING, joinCheck(forming.copy(phase = phase), players = 9))
        }
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
