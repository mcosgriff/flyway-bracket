package io.cosgriff.flywaybracket.ui.bracket

import io.cosgriff.flywaybracket.data.LeagueBracket
import io.cosgriff.flywaybracket.data.PicksNotice
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendarResult
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Bracket tab makes of a league's bracket: when picks lock, the round being played. Made-up league. */
class BracketStateTest {
    /** A season from Monday 1 March 2027, in Denver. */
    private val league = LeagueBracket(
        leagueId = "league-1",
        view = previewBeforeLock.view,
        preview = true,
        calendar = (SeasonCalendar.of(LocalDate.of(2027, 3, 1)) as SeasonCalendarResult.Valid).calendar,
        timeZone = ZoneId.of("America/Denver"),
        lockedByServer = false,
        picksUnsent = true,
        notice = PicksNotice.BRACKET_CHANGED,
    )

    @Test
    fun `before the lock the bracket is open, a preview, and says when it locks on the phone's clock`() {
        val state = bracketState(league, Instant.parse("2027-02-20T12:00:00Z"), ZoneId.of("America/New_York"))

        assertFalse(state.locked)
        assertEquals(Season.SPRING, state.season)
        // Midnight in Denver is 2 AM in New York.
        assertEquals(LocalDateTime.of(2027, 3, 1, 2, 0), state.locksAt)
        assertEquals(LocalDate.of(2027, 2, 27), state.previewUntil)
        assertNull(state.liveRound)
        assertEquals(LocalDate.of(2027, 3, 14), state.roundEnds[Round.ROUND_OF_32])
        assertTrue(state.picksUnsent)
        assertEquals(BracketNotice.BRACKET_CHANGED, state.notice)
        assertEquals(Round.ROUND_OF_64, state.firstRound)
    }

    @Test
    fun `after the lock the round being played is live, and the tab opens on it`() {
        // Wednesday of the second week, in Denver.
        val state = bracketState(league.copy(preview = false), Instant.parse("2027-03-10T18:00:00Z"), ZoneId.of("America/Denver"))

        assertTrue(state.locked)
        assertNull(state.previewUntil)
        assertEquals(Round.ROUND_OF_32, state.liveRound)
        assertEquals(Round.ROUND_OF_32, state.firstRound)
    }
}
