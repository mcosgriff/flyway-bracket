package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.api.CreateLeagueRequest
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.LeagueSummary
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.server.account.Accounts
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.account.SignedInPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * The league rules against each [LeagueStore]: [InMemoryLeaguesTest] needs nothing, [PostgresLeaguesTest]
 * a real Postgres. Players are made straight in the store, with no secret to hash. All names, codes and
 * counties are made up.
 */
abstract class LeaguesTest {
    /** A store holding no player and no league. */
    protected abstract fun players(): PlayerStore

    /** The leagues of [players]' players, holding none. */
    protected abstract fun leagueStore(players: PlayerStore): LeagueStore

    private val playerStore by lazy { players() }

    /** Wednesday 17 February 2027, noon in Denver. */
    protected val clock = HandClock(at(LocalDate.of(2027, 2, 17)))
    protected val leagues by lazy { Leagues(leagueStore(playerStore), clock) }
    private var made = 0

    protected fun newPlayer(displayName: String? = "Player ${++made}"): SignedInPlayer {
        val id = UUID.randomUUID()
        playerStore.create(id, "made-up-hash")
        playerStore.update(id, displayName, "US-ZZ-001")
        return SignedInPlayer(id)
    }

    private fun request(
        name: String = "Thursday Walkers",
        open: Boolean = false,
        timeZone: String = DENVER.id,
        seasonStart: LocalDate = START,
    ) = CreateLeagueRequest(name = name, open = open, timeZone = timeZone, seasonStart = seasonStart)

    protected fun create(owner: SignedInPlayer = newPlayer(), open: Boolean = false, seasonStart: LocalDate = START): LeagueSummary =
        inLeague(leagues.create(owner, request(open = open, seasonStart = seasonStart)))

    /** [count] new players join [league] by its code. */
    protected fun fill(league: LeagueSummary, count: Int): List<SignedInPlayer> = List(count) {
        newPlayer().also { inLeague(leagues.join(it, checkNotNull(league.inviteCode))) }
    }

    protected fun inLeague(outcome: LeagueOutcome): LeagueSummary {
        assertTrue(outcome.toString(), outcome is LeagueOutcome.InLeague)
        return (outcome as LeagueOutcome.InLeague).league
    }

    private fun refused(reason: LeagueRefusal) = LeagueOutcome.Refused(reason)

    // Creating

    @Test
    fun `creating a league makes the player its first member`() {
        val owner = newPlayer()

        val league = inLeague(leagues.create(owner, request(name = "  Thursday Walkers ")))

        assertEquals("Thursday Walkers", league.name)
        assertEquals(1, league.players)
        assertEquals(64, league.capacity)
        assertEquals(LeagueState.BEFORE_SEASON, league.state)
        assertEquals(listOf(league), leagues.mine(owner))
        UUID.fromString(league.id)
    }

    @Test
    fun `an invite code is 8 characters with no look-alikes`() {
        val codes = List(20) { checkNotNull(create().inviteCode) }

        codes.forEach { code ->
            assertEquals(8, code.length)
            assertTrue(code, code.all { it in InviteCode.ALPHABET })
            assertTrue(code, code.none { it in "0O1IL" })
        }
        assertEquals(codes.size, codes.toSet().size)
    }

    @Test
    fun `a name of 3 to 40 characters`() {
        inLeague(leagues.create(newPlayer(), request(name = "Owl")))
        inLeague(leagues.create(newPlayer(), request(name = "W".repeat(40))))
        assertEquals(refused(LeagueRefusal.NAME_INVALID), leagues.create(newPlayer(), request(name = "Ow")))
        assertEquals(refused(LeagueRefusal.NAME_INVALID), leagues.create(newPlayer(), request(name = "W".repeat(41))))
        assertEquals(refused(LeagueRefusal.NAME_INVALID), leagues.create(newPlayer(), request(name = "   ")))
    }

    @Test
    fun `a time zone the server knows`() {
        listOf("Mars/Olympus_Mons", "+02:00", "UTC+2", "").forEach { zone ->
            assertEquals(zone, refused(LeagueRefusal.TIME_ZONE_UNKNOWN), leagues.create(newPlayer(), request(timeZone = zone)))
        }
        inLeague(leagues.create(newPlayer(), request(timeZone = "Pacific/Auckland")))
    }

    @Test
    fun `a season start on a Monday at least 7 days ahead`() {
        assertEquals(refused(LeagueRefusal.START_NOT_A_MONDAY), leagues.create(newPlayer(), request(seasonStart = START.plusDays(1))))
        // Monday 22 February is 5 days after Wednesday 17 February.
        assertEquals(refused(LeagueRefusal.START_TOO_SOON), leagues.create(newPlayer(), request(seasonStart = LocalDate.of(2027, 2, 22))))
        clock.now = at(LocalDate.of(2027, 2, 22), LocalTime.MIDNIGHT)
        inLeague(leagues.create(newPlayer(), request(seasonStart = START)))
    }

    @Test
    fun `seven days ahead is counted in the league's time zone`() {
        // Monday 22 February, 23:30 in Denver, is already Tuesday in Auckland: 1 March is 6 days ahead there.
        clock.now = at(LocalDate.of(2027, 2, 22), LocalTime.of(23, 30))

        inLeague(leagues.create(newPlayer(), request(timeZone = DENVER.id)))
        assertEquals(refused(LeagueRefusal.START_TOO_SOON), leagues.create(newPlayer(), request(timeZone = "Pacific/Auckland")))
    }

    @Test
    fun `a player with no display name cannot create or join`() {
        val nameless = newPlayer(displayName = null)
        val league = create()

        assertEquals(refused(LeagueRefusal.NO_DISPLAY_NAME), leagues.create(nameless, request()))
        assertEquals(refused(LeagueRefusal.NO_DISPLAY_NAME), leagues.join(nameless, checkNotNull(league.inviteCode)))
        assertEquals(refused(LeagueRefusal.NO_DISPLAY_NAME), leagues.autoJoin(nameless))
    }

    @Test
    fun `a deleted player is refused`() {
        val gone = SignedInPlayer(UUID.randomUUID())

        assertEquals(refused(LeagueRefusal.NO_SUCH_PLAYER), leagues.create(gone, request()))
        assertEquals(refused(LeagueRefusal.NO_SUCH_PLAYER), leagues.autoJoin(gone))
    }

    // One league at a time

    @Test
    fun `a second league for one player is refused`() {
        val player = newPlayer()
        val first = create(player)
        val otherOwner = newPlayer()
        val other = create(otherOwner, open = true)

        assertEquals(refused(LeagueRefusal.ALREADY_IN_A_LEAGUE), leagues.create(player, request()))
        assertEquals(refused(LeagueRefusal.ALREADY_IN_A_LEAGUE), leagues.join(player, checkNotNull(other.inviteCode)))
        assertEquals(refused(LeagueRefusal.ALREADY_IN_A_LEAGUE), leagues.autoJoin(player))
        assertEquals(listOf(first.id), leagues.mine(player).map { it.id })
        assertEquals(1, leagues.mine(otherOwner).single().players)
    }

    @Test
    fun `joining your own league again is refused as already a member`() {
        val owner = newPlayer()
        val league = create(owner)

        assertEquals(refused(LeagueRefusal.ALREADY_A_MEMBER), leagues.join(owner, checkNotNull(league.inviteCode)))
    }

    @Test
    fun `one player joining two leagues at once gets one`() {
        val player = newPlayer()
        val first = create()
        val second = create()

        val outcomes = atOnce(
            { leagues.join(player, checkNotNull(first.inviteCode)) },
            { leagues.join(player, checkNotNull(second.inviteCode)) },
        )

        assertEquals(1, outcomes.count { it is LeagueOutcome.InLeague })
        assertEquals(1, outcomes.count { it == refused(LeagueRefusal.ALREADY_IN_A_LEAGUE) })
        assertEquals(1, leagues.mine(player).size)
    }

    // Joining

    @Test
    fun `joining by invite code`() {
        val league = create()
        val player = newPlayer()

        val joined = inLeague(leagues.join(player, checkNotNull(league.inviteCode)))

        assertEquals(league.copy(players = 2), joined)
        assertEquals(listOf(joined), leagues.mine(player))
    }

    @Test
    fun `an invite code is read without case and without spaces at either end`() {
        val code = checkNotNull(create().inviteCode)

        inLeague(leagues.join(newPlayer(), "  ${code.lowercase()} "))
    }

    @Test
    fun `an unknown invite code is refused`() {
        create()

        listOf("ABCDEFGH", "ABCDEFG", "ABCD EFGH", "ABCDEFG0", "").forEach { code ->
            assertEquals(code, refused(LeagueRefusal.INVITE_CODE_UNKNOWN), leagues.join(newPlayer(), code))
        }
    }

    @Test
    fun `the 65th player is refused`() {
        val owner = newPlayer()
        val league = create(owner)
        fill(league, 63)

        val last = newPlayer()
        assertEquals(refused(LeagueRefusal.FULL), leagues.join(last, checkNotNull(league.inviteCode)))
        assertEquals(64, leagues.mine(owner).single().players)
        assertEquals(emptyList<LeagueSummary>(), leagues.mine(last))
    }

    @Test
    fun `two players joining the last seat at once`() {
        val league = create()
        fill(league, 62)
        val first = newPlayer()
        val second = newPlayer()

        val outcomes = atOnce(
            { leagues.join(first, checkNotNull(league.inviteCode)) },
            { leagues.join(second, checkNotNull(league.inviteCode)) },
        )

        assertEquals(outcomes.toString(), 1, outcomes.count { it is LeagueOutcome.InLeague })
        assertEquals(outcomes.toString(), 1, outcomes.count { it == refused(LeagueRefusal.FULL) })
        val winner = if (outcomes[0] is LeagueOutcome.InLeague) first else second
        assertEquals(64, leagues.mine(winner).single().players)
    }

    @Test
    fun `a join after the start is refused`() {
        val league = create()
        fill(league, 7)

        // Sunday 28 February, 23:59 in Denver: still before the start.
        clock.now = at(LocalDate.of(2027, 2, 28), LocalTime.of(23, 59))
        inLeague(leagues.join(newPlayer(), checkNotNull(league.inviteCode)))

        // Monday 1 March, 00:00 in Denver.
        clock.now = at(START, LocalTime.MIDNIGHT)
        assertEquals(refused(LeagueRefusal.JOINING_CLOSED), leagues.join(newPlayer(), checkNotNull(league.inviteCode)))
    }

    @Test
    fun `the start arrives at midnight in the league's time zone, not the server's`() {
        val league = create()
        fill(league, 7)

        // 06:59 UTC on 1 March is 23:59 on 28 February in Denver.
        clock.now = Instant.parse("2027-03-01T06:59:00Z")
        inLeague(leagues.join(newPlayer(), checkNotNull(league.inviteCode)))
        clock.now = Instant.parse("2027-03-01T07:00:00Z")
        assertEquals(refused(LeagueRefusal.JOINING_CLOSED), leagues.join(newPlayer(), checkNotNull(league.inviteCode)))
    }

    // The start

    @Test
    fun `with 8 players the season starts`() {
        val owner = newPlayer()
        val league = create(owner)
        fill(league, 7)

        clock.now = at(START)

        val detail = checkNotNull(leagues.detail(owner, league.id))
        assertEquals(LeagueState.IN_SEASON, detail.league.state)
        assertEquals(START, detail.seasonStart)
        assertEquals(LocalDate.of(2027, 4, 11), detail.seasonEnd)
        assertEquals(Season.SPRING, detail.season)
    }

    @Test
    fun `the postponed start`() {
        val owner = newPlayer()
        val league = create(owner)
        fill(league, 6)

        // 7 players on 1 March: the start moves to 8 March, and the league still takes players.
        clock.now = at(START)
        val postponed = checkNotNull(leagues.detail(owner, league.id))
        assertEquals(LeagueState.BEFORE_SEASON, postponed.league.state)
        assertEquals(LocalDate.of(2027, 3, 8), postponed.seasonStart)
        inLeague(leagues.join(newPlayer(), checkNotNull(league.inviteCode)))

        // 8 players on 8 March: it starts.
        clock.now = at(LocalDate.of(2027, 3, 8))
        val started = checkNotNull(leagues.detail(owner, league.id))
        assertEquals(LeagueState.IN_SEASON, started.league.state)
        assertEquals(LocalDate.of(2027, 3, 8), started.seasonStart)
        assertEquals(LocalDate.of(2027, 4, 18), started.seasonEnd)
        assertEquals(refused(LeagueRefusal.JOINING_CLOSED), leagues.join(newPlayer(), checkNotNull(league.inviteCode)))
    }

    @Test
    fun `the start moves back four times, then the league is closed and its players told`() {
        val owner = newPlayer()
        val league = create(owner)
        fill(league, 2)

        clock.now = at(LocalDate.of(2027, 3, 28))
        assertEquals(LocalDate.of(2027, 3, 29), checkNotNull(leagues.detail(owner, league.id)).seasonStart)
        assertEquals(LeagueState.BEFORE_SEASON, leagues.current(owner.id)?.state)

        clock.now = at(LocalDate.of(2027, 3, 29))
        assertEquals(LeagueState.CLOSED, leagues.current(owner.id)?.state)
        assertEquals(LeagueState.CLOSED, Accounts(playerStore, leagues).profile(owner)?.league?.state)
        assertEquals(refused(LeagueRefusal.JOINING_CLOSED), leagues.join(newPlayer(), checkNotNull(league.inviteCode)))
    }

    @Test
    fun `a player whose league closed can join another`() {
        val owner = newPlayer()
        create(owner)
        clock.now = at(LocalDate.of(2027, 3, 29))

        val next = inLeague(leagues.create(owner, request(seasonStart = LocalDate.of(2027, 4, 12))))

        assertEquals(next, leagues.current(owner.id))
        assertEquals(listOf(LeagueState.BEFORE_SEASON, LeagueState.CLOSED), leagues.mine(owner).map { it.state })
    }

    @Test
    fun `a finished league leaves its players free for the next`() {
        val owner = newPlayer()
        val league = create(owner)
        fill(league, 7)

        clock.now = at(LocalDate.of(2027, 4, 11))
        assertEquals(LeagueState.IN_SEASON, leagues.current(owner.id)?.state)
        assertEquals(refused(LeagueRefusal.ALREADY_IN_A_LEAGUE), leagues.create(owner, request(seasonStart = LocalDate.of(2027, 4, 19))))

        clock.now = at(LocalDate.of(2027, 4, 12))
        assertEquals(LeagueState.FINISHED, leagues.current(owner.id)?.state)
        val next = inLeague(leagues.create(owner, request(seasonStart = LocalDate.of(2027, 4, 19))))
        assertEquals(next, leagues.current(owner.id))
    }

    // Auto-join

    @Test
    fun `auto-join with no open league opens one on the second Monday from today, in UTC`() {
        val player = newPlayer()

        val opened = inLeague(leagues.autoJoin(player))

        assertEquals(1, opened.players)
        assertEquals(LeagueState.BEFORE_SEASON, opened.state)
        assertTrue(opened.name, opened.name.startsWith("Flyway "))
        val detail = checkNotNull(leagues.detail(player, opened.id))
        assertEquals("Z", detail.timeZone)
        // Wednesday 17 February: the next Monday is the 22nd, the second 1 March.
        assertEquals(START, detail.seasonStart)
    }

    @Test
    fun `the next player auto-joins the league the first opened`() {
        val opened = inLeague(leagues.autoJoin(newPlayer()))

        val joined = inLeague(leagues.autoJoin(newPlayer()))

        assertEquals(opened.id, joined.id)
        assertEquals(2, joined.players)
    }

    @Test
    fun `auto-join picks the open league with the earliest start`() {
        val later = create(open = true, seasonStart = LocalDate.of(2027, 3, 15))
        val earliest = create(open = true, seasonStart = START)
        create(open = true, seasonStart = LocalDate.of(2027, 3, 8))

        assertEquals(earliest.id, inLeague(leagues.autoJoin(newPlayer())).id)
        assertNotEquals(later.id, earliest.id)
    }

    @Test
    fun `auto-join passes over invite-only, full and started leagues`() {
        create(open = false, seasonStart = START)
        fill(create(open = true, seasonStart = START), 63)
        val started = create(open = true, seasonStart = START)
        fill(started, 7)
        val withRoom = create(open = true, seasonStart = LocalDate.of(2027, 3, 8))
        clock.now = at(START)

        assertEquals(withRoom.id, inLeague(leagues.autoJoin(newPlayer())).id)
    }

    @Test
    fun `auto-join judges a postponed league by its new start`() {
        // Made first, starting on 8 March.
        val madeFirst = create(open = true, seasonStart = LocalDate.of(2027, 3, 8))
        // Made second, starting on 1 March; short of players then, so it moves to 8 March too.
        create(open = true, seasonStart = START)
        clock.now = at(START)

        // Both start on 8 March now; the one made first is chosen.
        assertEquals(madeFirst.id, inLeague(leagues.autoJoin(newPlayer())).id)
    }

    // Leaving

    @Test
    fun `leaving before the start frees the seat and the player`() {
        val player = newPlayer()
        val owner = newPlayer()
        val league = create(owner)
        inLeague(leagues.join(player, checkNotNull(league.inviteCode)))

        assertTrue(leagues.leave(player, league.id))

        assertEquals(emptyList<LeagueSummary>(), leagues.mine(player))
        assertNull(leagues.detail(player, league.id))
        assertEquals(1, leagues.mine(owner).single().players)
        inLeague(leagues.join(player, checkNotNull(league.inviteCode)))
    }

    @Test
    fun `leaving after the start takes the player out of the count`() {
        val owner = newPlayer()
        val league = create(owner)
        val leaver = fill(league, 8).first()
        clock.now = at(START.plusDays(2))

        assertTrue(leagues.leave(leaver, league.id))

        val detail = checkNotNull(leagues.detail(owner, league.id))
        assertEquals(LeagueState.IN_SEASON, detail.league.state)
        assertEquals(8, detail.league.players)
        assertFalse(detail.members.any { it.displayName == "Player 2" })
        assertNull(leagues.detail(leaver, league.id))
        assertEquals(refused(LeagueRefusal.JOINING_CLOSED), leagues.join(leaver, checkNotNull(league.inviteCode)))
        inLeague(leagues.create(leaver, request(seasonStart = LocalDate.of(2027, 3, 15))))
    }

    @Test
    fun `leaving a league the player is not in`() {
        val league = create()

        assertFalse(leagues.leave(newPlayer(), league.id))
        assertFalse(leagues.leave(newPlayer(), UUID.randomUUID().toString()))
        assertFalse(leagues.leave(newPlayer(), "league-1"))
    }

    @Test
    fun `a league's start is settled with a player who leaves on the day still counted`() {
        val owner = newPlayer()
        val league = create(owner)
        val leaver = fill(league, 7).first()

        // Nothing read the league when its start arrived with 8 players. It started then.
        clock.now = at(START.plusDays(1))
        assertTrue(leagues.leave(leaver, league.id))

        assertEquals(LeagueState.IN_SEASON, checkNotNull(leagues.detail(owner, league.id)).league.state)
    }

    // Deleting an account

    @Test
    fun `a deleted player leaves their league`() {
        val owner = newPlayer()
        val league = create(owner)
        val deleted = fill(league, 1).single()

        Accounts(playerStore, leagues).delete(deleted)

        assertEquals(1, leagues.mine(owner).single().players)
        assertEquals(listOf("Player 1"), checkNotNull(leagues.detail(owner, league.id)).members.map { it.displayName })
    }

    @Test
    fun `a deletion on the start day does not postpone a league that had 8`() {
        val owner = newPlayer()
        val league = create(owner)
        val deleted = fill(league, 7).first()
        clock.now = at(START.plusDays(3))

        Accounts(playerStore, leagues).delete(deleted)

        val detail = checkNotNull(leagues.detail(owner, league.id))
        assertEquals(LeagueState.IN_SEASON, detail.league.state)
        assertEquals(START, detail.seasonStart)
        assertEquals(7, detail.league.players)
    }

    // What a member sees

    @Test
    fun `members by display name, with points and rank`() {
        val owner = newPlayer(displayName = "Kestrel")
        val league = create(owner)
        inLeague(leagues.join(newPlayer(displayName = "avocet"), checkNotNull(league.inviteCode)))
        inLeague(leagues.join(newPlayer(displayName = "Bittern"), checkNotNull(league.inviteCode)))

        val members = checkNotNull(leagues.detail(owner, league.id)).members

        assertEquals(listOf("avocet", "Bittern", "Kestrel"), members.map { it.displayName })
        assertEquals(listOf(0, 0, 0), members.map { it.points })
        assertEquals(listOf(1, 1, 1), members.map { it.rank })
        assertEquals(listOf(false, false, true), members.map { it.you })
    }

    @Test
    fun `only a member sees a league`() {
        val owner = newPlayer()
        val league = create(owner)

        assertNull(leagues.detail(newPlayer(), league.id))
        assertNull(leagues.detail(owner, UUID.randomUUID().toString()))
        assertNull(leagues.detail(owner, "league-1"))
    }

    @Test
    fun `the profile shows the player's league`() {
        val owner = newPlayer()
        val accounts = Accounts(playerStore, leagues)
        assertNull(accounts.profile(owner)?.league)

        val league = create(owner)

        assertEquals(league, accounts.profile(owner)?.league)
    }

    /** Runs both at the same moment, each on its own thread. */
    private fun atOnce(vararg actions: () -> LeagueOutcome): List<LeagueOutcome> {
        val pool = Executors.newFixedThreadPool(actions.size)
        val barrier = CyclicBarrier(actions.size)
        try {
            val futures = actions.map { action ->
                pool.submit<LeagueOutcome> {
                    barrier.await()
                    action()
                }
            }
            return futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    protected companion object {
        val DENVER: ZoneId = ZoneId.of("America/Denver")

        /** Monday 1 March 2027. */
        val START: LocalDate = LocalDate.of(2027, 3, 1)

        fun at(date: LocalDate, time: LocalTime = LocalTime.NOON, zone: ZoneId = DENVER): Instant =
            ZonedDateTime.of(date, time, zone).toInstant()
    }
}
