package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.api.BracketResponse
import io.cosgriff.flywaybracket.api.CreateLeagueRequest
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.LeagueSummary
import io.cosgriff.flywaybracket.api.MatchupSlot
import io.cosgriff.flywaybracket.api.PicksResponse
import io.cosgriff.flywaybracket.api.SubmitPicksRequest
import io.cosgriff.flywaybracket.domain.bracket.BirdGroup
import io.cosgriff.flywaybracket.domain.bracket.Field
import io.cosgriff.flywaybracket.domain.bracket.FieldResult
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendarResult
import io.cosgriff.flywaybracket.domain.league.CloseReason
import io.cosgriff.flywaybracket.domain.league.InviteCode
import io.cosgriff.flywaybracket.server.account.Accounts
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.account.SignedInPlayer
import io.cosgriff.flywaybracket.server.pack.TEST_HOME_COUNTY
import io.cosgriff.flywaybracket.server.pack.testCharts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
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

    /** Lists [id] as deleted at [at], as an operator copying the list in after a restore does. */
    protected abstract fun listAsDeleted(id: UUID, at: Instant)

    protected val playerStore by lazy { players() }

    /** Wednesday 17 February 2027, noon in Denver. */
    protected val clock = HandClock(at(LocalDate.of(2027, 2, 17)))

    /** Fieldland's made-up counties, and `US-ZZ-001` with 80 made-up migrants. */
    protected val charts = testCharts()

    /** Each time a stored field replaced one with birds: the league and the matchups whose birds changed. */
    protected val previewChanges = mutableListOf<Pair<UUID, List<MatchupSlot>>>()

    protected val leagues by lazy {
        Leagues(leagueStore(playerStore), charts, clock, previewChanged = { id, slots -> previewChanges += id to slots })
    }
    private var made = 0

    protected fun newPlayer(displayName: String? = "Player ${++made}", homeCounty: String = TEST_HOME_COUNTY): SignedInPlayer {
        val id = UUID.randomUUID()
        playerStore.create(id, "made-up-hash")
        playerStore.update(id, displayName, homeCounty)
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
    protected fun fill(league: LeagueSummary, count: Int, homeCounty: String = TEST_HOME_COUNTY): List<SignedInPlayer> = List(count) {
        newPlayer(homeCounty = homeCounty).also { inLeague(leagues.join(it, checkNotNull(league.inviteCode))) }
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
        val told = Accounts(playerStore, leagues, charts).profile(owner)?.league
        assertEquals(LeagueState.CLOSED, told?.state)
        assertEquals(CloseReason.TOO_FEW_PLAYERS, told?.closedBecause)
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

        Accounts(playerStore, leagues, charts).delete(deleted)

        assertEquals(1, leagues.mine(owner).single().players)
        assertEquals(listOf("Player 1"), checkNotNull(leagues.detail(owner, league.id)).members.map { it.displayName })
    }

    @Test
    fun `a deletion on the start day does not postpone a league that had 8`() {
        val owner = newPlayer()
        val league = create(owner)
        val deleted = fill(league, 7).first()
        clock.now = at(START.plusDays(3))

        Accounts(playerStore, leagues, charts).delete(deleted)

        val detail = checkNotNull(leagues.detail(owner, league.id))
        assertEquals(LeagueState.IN_SEASON, detail.league.state)
        assertEquals(START, detail.seasonStart)
        assertEquals(7, detail.league.players)
    }

    @Test
    fun `an account a restore brought back is deleted again as deleting it would, and leaves its league`() {
        val owner = newPlayer()
        val league = create(owner)
        val restored = fill(league, 1).single()
        listAsDeleted(restored.id, clock.instant().minus(Duration.ofDays(1)))

        assertEquals(1, Accounts(playerStore, leagues, charts, clock = clock).deleteAgainListed())

        assertNull(playerStore.profile(restored.id))
        assertEquals(emptyList<UUID>(), playerStore.deletedButPresent())
        assertEquals(1, leagues.mine(owner).single().players)
        assertEquals(listOf("Player 1"), checkNotNull(leagues.detail(owner, league.id)).members.map { it.displayName })
    }

    // What a member sees

    @Test
    fun `members level on points are listed in the order they joined, with points and rank`() {
        val owner = newPlayer(displayName = "Kestrel")
        val league = create(owner)
        inLeague(leagues.join(newPlayer(displayName = "avocet"), checkNotNull(league.inviteCode)))
        inLeague(leagues.join(newPlayer(displayName = "Bittern"), checkNotNull(league.inviteCode)))

        val members = checkNotNull(leagues.detail(owner, league.id)).members

        assertEquals(listOf("Kestrel", "avocet", "Bittern"), members.map { it.displayName })
        assertEquals(listOf(0, 0, 0), members.map { it.points })
        assertEquals(listOf(0, 0, 0), members.map { it.rightPicks })
        assertEquals(listOf(1, 1, 1), members.map { it.rank })
        assertEquals(listOf(true, false, false), members.map { it.you })
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
        val accounts = Accounts(playerStore, leagues, charts)
        assertNull(accounts.profile(owner)?.league)

        val league = create(owner)

        assertEquals(league, accounts.profile(owner)?.league)
    }

    // Seeding (#34), with the made-up pack of Fieldland

    protected fun bracketOf(player: SignedInPlayer, league: LeagueSummary): BracketResponse {
        val outcome = leagues.bracket(player, league.id)
        assertTrue(outcome.toString(), outcome is BracketOutcome.Ready)
        return (outcome as BracketOutcome.Ready).bracket
    }

    private fun BracketResponse.quarter(group: BirdGroup): List<String> =
        teams.filter { it.quarter == group }.sortedBy { it.seed }.map { it.commonName }

    /** `Test <family> 20` down to `Test <family> 05`: seeds 1 to 16 of a group with 20 birds. */
    private fun strongest16(family: String) = (20 downTo 5).map { "Test $family ${it.toString().padStart(2, '0')}" }

    @Test
    fun `when the season starts its bracket is seeded from the members' home counties`() {
        val owner = newPlayer(homeCounty = PLENTY)
        val league = create(owner)
        fill(league, 7, homeCounty = PLENTY)
        clock.now = at(START, LocalTime.of(0, 1))

        leagues.settleDue()

        assertEquals(LeagueState.IN_SEASON, leagues.mine(owner).single().state)
        val bracket = bracketOf(owner, league)
        assertFalse(bracket.preview)
        assertEquals(64, bracket.teams.size)
        assertEquals(strongest16("Warbler"), bracket.quarter(BirdGroup.SONGBIRDS))
        assertEquals(strongest16("Sandpiper"), bracket.quarter(BirdGroup.WATERBIRDS))
        assertEquals(strongest16("Swallow"), bracket.quarter(BirdGroup.FLIERS))
        assertEquals(strongest16("Teal"), bracket.quarter(BirdGroup.WATERFOWL))
        assertEquals(MatchupSlot.all, bracket.matchups.map { it.slot })
        // Seeds 1 and 16 of the top quarter meet at the top; seeds 2 and 15 of the bottom one at the bottom.
        assertEquals("Test Warbler 20" to "Test Warbler 05", bracket.matchups[0].upper to bracket.matchups[0].lower)
        assertEquals("Test Teal 19" to "Test Teal 06", bracket.matchups[31].upper to bracket.matchups[31].lower)
        // No round is decided yet.
        assertTrue(bracket.matchups.all { it.winner == null })
        assertTrue(bracket.matchups.filter { it.slot.round != Round.ROUND_OF_64 }.all { it.upper == null && it.lower == null })
    }

    @Test
    fun `a short quarter is filled with the strongest birds of the other groups`() {
        val owner = newPlayer(homeCounty = FEW_DUCKS)
        val league = create(owner)
        fill(league, 7, homeCounty = FEW_DUCKS)
        clock.now = at(START)

        leagues.settleDue()

        val waterfowl = bracketOf(owner, league).quarter(BirdGroup.WATERFOWL)
        assertEquals(16, waterfowl.size)
        assertEquals("Test Teal 06", waterfowl.first())
        assertEquals(6, waterfowl.count { BirdGroup.of(it) == BirdGroup.WATERFOWL })
        assertEquals(64, bracketOf(owner, league).teams.map { it.commonName }.toSet().size)
    }

    @Test
    fun `with too few migrating birds the league is closed when its season would start`() {
        val owner = newPlayer(homeCounty = SPARSE)
        val league = create(owner)
        fill(league, 7, homeCounty = SPARSE)
        clock.now = at(START)

        // Read before anything saved the start: the league is closed already.
        assertEquals(CloseReason.TOO_FEW_BIRDS, leagues.current(owner.id)?.closedBecause)
        leagues.settleDue()

        val summary = leagues.mine(owner).single()
        assertEquals(LeagueState.CLOSED, summary.state)
        assertEquals(CloseReason.TOO_FEW_BIRDS, summary.closedBecause)
        assertEquals(CloseReason.TOO_FEW_BIRDS, Accounts(playerStore, leagues, charts).profile(owner)?.league?.closedBecause)
        assertEquals(BracketOutcome.NotReady, leagues.bracket(owner, league.id))
        assertEquals(refused(LeagueRefusal.JOINING_CLOSED), leagues.join(newPlayer(), checkNotNull(league.inviteCode)))
    }

    @Test
    fun `a member whose home county is in no pack is left out of seeding, and told so`() {
        val owner = newPlayer(homeCounty = FEW_DUCKS)
        val league = create(owner)
        val outsiders = fill(league, 7, homeCounty = OUTSIDE_EVERY_PACK)
        val accounts = Accounts(playerStore, leagues, charts)
        assertEquals(false, accounts.profile(outsiders.first())?.homeCountyInPacks)
        assertEquals(true, accounts.profile(owner)?.homeCountyInPacks)
        clock.now = at(START)

        leagues.settleDue()

        // The outsiders count toward the 8 players, but the field is Few Ducks' alone.
        assertEquals(LeagueState.IN_SEASON, leagues.mine(owner).single().state)
        val bins = (SeasonCalendar.of(START) as SeasonCalendarResult.Valid).calendar.allBins()
        val fewDucksAlone = Field.choose(listOf(checkNotNull(charts.chartFor(FEW_DUCKS))), bins) as FieldResult.Seeded
        assertEquals(
            fewDucksAlone.teams.map { Triple(it.commonName, it.quarter, it.seed) }.toSet(),
            bracketOf(owner, league).teams.map { Triple(it.commonName, it.quarter.name, it.seed) }.toSet(),
        )
    }

    @Test
    fun `the job running twice seeds once`() {
        val owner = newPlayer(homeCounty = PLENTY)
        val league = create(owner)
        val members = fill(league, 7, homeCounty = PLENTY) + owner
        // Nothing read the league on the day its field was fixed: the start seeds it.
        clock.now = at(START, LocalTime.of(0, 1))
        leagues.settleDue()
        val seeded = bracketOf(owner, league)

        // Every member moves to Few Ducks. Seeding again would fill the waterfowl quarter from other groups.
        members.forEach { playerStore.update(it.id, null, FEW_DUCKS) }
        clock.now = at(START, LocalTime.of(0, 2))
        leagues.settleDue()
        leagues.settleDue()

        assertEquals(seeded, bracketOf(owner, league))
        assertEquals(strongest16("Teal"), bracketOf(owner, league).quarter(BirdGroup.WATERFOWL))
        assertEquals(LeagueState.IN_SEASON, leagues.mine(owner).single().state)
    }

    @Test
    fun `before the field is fixed the bracket is a preview, seeded again when the members change`() {
        val owner = newPlayer(homeCounty = PLENTY)
        val league = create(owner)

        val first = bracketOf(owner, league)
        assertTrue(first.preview)
        assertEquals(strongest16("Teal"), first.quarter(BirdGroup.WATERFOWL))
        // Asking again seeds the same field; there was nothing to pick against before the first.
        assertEquals(first, bracketOf(owner, league))
        assertEquals(emptyList<Pair<UUID, List<MatchupSlot>>>(), previewChanges)

        // A player from Few Ducks joins. Its six teal now count for both members, the other teal for one.
        val joiner = fill(league, 1, homeCounty = FEW_DUCKS).single()
        val second = bracketOf(owner, league)
        assertTrue(second.preview)
        assertNotEquals(first.quarter(BirdGroup.WATERFOWL).toSet(), second.quarter(BirdGroup.WATERFOWL).toSet())
        assertEquals(first.quarter(BirdGroup.SONGBIRDS), second.quarter(BirdGroup.SONGBIRDS))
        // The matchups whose birds changed are the waterfowl quarter's, and those they feed.
        val (changedLeague, changed) = previewChanges.single()
        assertEquals(league.id, changedLeague.toString())
        assertTrue(changed.toString(), changed.isNotEmpty())
        assertTrue(changed.toString(), changed.filter { it.round == Round.ROUND_OF_64 }.all { it.position >= 24 })
        assertTrue(changed.toString(), MatchupSlot(62) in changed)

        // The joiner's home county changes to Plenty: the first field is back.
        playerStore.update(joiner.id, null, PLENTY)
        assertEquals(first.teams, bracketOf(owner, league).teams)
        assertEquals(2, previewChanges.size)
    }

    @Test
    fun `the field is fixed 48 hours before the start, and a late joiner does not change it`() {
        val owner = newPlayer(homeCounty = PLENTY)
        val league = create(owner)
        fill(league, 7, homeCounty = PLENTY)

        // Friday: still a preview.
        clock.now = at(LocalDate.of(2027, 2, 26))
        val preview = bracketOf(owner, league)
        assertTrue(preview.preview)

        // Saturday: the job fixes the field.
        clock.now = at(LocalDate.of(2027, 2, 27), LocalTime.of(0, 1))
        leagues.settleDue()

        // Sunday: a player from Few Ducks can still join, but the field stays as it was.
        clock.now = at(LocalDate.of(2027, 2, 28))
        inLeague(leagues.join(newPlayer(homeCounty = FEW_DUCKS), checkNotNull(league.inviteCode)))
        val fixed = bracketOf(owner, league)
        assertFalse(fixed.preview)
        assertEquals(preview.teams, fixed.teams)

        // Monday: the season starts with that field.
        clock.now = at(START)
        leagues.settleDue()
        assertEquals(LeagueState.IN_SEASON, leagues.mine(owner).single().state)
        assertEquals(fixed, bracketOf(owner, league))
    }

    @Test
    fun `only a member sees a bracket, and only once there is a field`() {
        val owner = newPlayer()
        val league = create(owner)

        assertEquals(BracketOutcome.NotFound, leagues.bracket(newPlayer(), league.id))
        assertEquals(BracketOutcome.NotFound, leagues.bracket(owner, UUID.randomUUID().toString()))
        assertEquals(BracketOutcome.NotFound, leagues.bracket(owner, "league-1"))

        val sparseOwner = newPlayer(homeCounty = SPARSE)
        val sparse = create(sparseOwner)
        assertEquals(BracketOutcome.NotReady, leagues.bracket(sparseOwner, sparse.id))
    }

    // Picks (#35)

    /** A whole bracket of [bracket]'s field in which the bird drawn above wins every matchup. */
    protected fun upperWinsEverywhere(bracket: BracketResponse): Map<MatchupSlot, String> {
        val picks = sortedMapOf<MatchupSlot, String>()
        MatchupSlot.all.forEach { slot ->
            picks[slot] = if (slot.round == Round.ROUND_OF_64) {
                checkNotNull(bracket.matchups[slot.index].upper)
            } else {
                picks.getValue(MatchupSlot.of(Round.entries[slot.round.ordinal - 1], 2 * slot.position))
            }
        }
        return picks
    }

    private fun submit(player: SignedInPlayer, league: LeagueSummary, picks: Map<MatchupSlot, String>): PicksOutcome =
        leagues.submitPicks(player, league.id, SubmitPicksRequest(picks))

    private fun shown(outcome: PicksOutcome): PicksResponse {
        assertTrue(outcome.toString(), outcome is PicksOutcome.Shown)
        return (outcome as PicksOutcome.Shown).picks
    }

    private fun memberKey(asking: SignedInPlayer, league: LeagueSummary, displayName: String): String =
        checkNotNull(leagues.detail(asking, league.id)).members.single { it.displayName == displayName }.memberKey

    @Test
    fun `a whole bracket is stored and read back`() {
        val owner = newPlayer()
        val league = create(owner)
        val picks = upperWinsEverywhere(bracketOf(owner, league))

        assertEquals(PicksResponse(picks, locked = false), shown(submit(owner, league, picks)))

        assertEquals(PicksResponse(picks, locked = false), shown(leagues.picks(owner, league.id)))
    }

    @Test
    fun `a part of a bracket is stored, in place of every pick before it`() {
        val owner = newPlayer()
        val league = create(owner)
        assertEquals(PicksResponse(emptyMap(), locked = false), shown(leagues.picks(owner, league.id)))
        val whole = upperWinsEverywhere(bracketOf(owner, league))
        shown(submit(owner, league, whole))

        val firstRound = whole.filterKeys { it.round == Round.ROUND_OF_64 }
        shown(submit(owner, league, firstRound))

        assertEquals(firstRound, shown(leagues.picks(owner, league.id)).picks)
        shown(submit(owner, league, emptyMap()))
        assertEquals(emptyMap<MatchupSlot, String>(), shown(leagues.picks(owner, league.id)).picks)
    }

    @Test
    fun `an inconsistent bracket is refused at its first bad slot, and nothing is stored`() {
        val owner = newPlayer()
        val league = create(owner)
        val bracket = bracketOf(owner, league)
        val whole = upperWinsEverywhere(bracket)
        shown(submit(owner, league, whole))

        // The first matchup's lower bird is picked to win the next one, having lost the first.
        val loserGoesOn = whole + (MatchupSlot(32) to checkNotNull(bracket.matchups[0].lower))
        assertEquals(
            PicksOutcome.Refused(PicksRefusal.NOT_PICKED_TO_REACH, MatchupSlot(32)),
            submit(owner, league, loserGoesOn),
        )
        // The final with nothing before it.
        assertEquals(
            PicksOutcome.Refused(PicksRefusal.NOT_PICKED_TO_REACH, MatchupSlot(62)),
            submit(owner, league, mapOf(MatchupSlot(62) to whole.getValue(MatchupSlot(62)))),
        )
        // A bird drawn in another matchup, and one in no matchup at all.
        assertEquals(
            PicksOutcome.Refused(PicksRefusal.NOT_IN_MATCHUP, MatchupSlot(5)),
            submit(owner, league, mapOf(MatchupSlot(5) to checkNotNull(bracket.matchups[6].upper))),
        )
        assertEquals(
            PicksOutcome.Refused(PicksRefusal.NOT_IN_MATCHUP, MatchupSlot(0)),
            submit(owner, league, mapOf(MatchupSlot(0) to "Test Unknown Bird")),
        )

        assertEquals(whole, shown(leagues.picks(owner, league.id)).picks)
    }

    @Test
    fun `picks lock at midnight on the first Monday in the league's time zone`() {
        val owner = newPlayer()
        val league = create(owner)
        fill(league, 7)
        val picks = upperWinsEverywhere(bracketOf(owner, league))
        val lock = at(START, LocalTime.MIDNIGHT)

        clock.now = lock.minusSeconds(1)
        shown(submit(owner, league, picks.filterKeys { it.round == Round.ROUND_OF_64 }))

        clock.now = lock.plusSeconds(1)
        assertEquals(PicksOutcome.Locked, submit(owner, league, picks))
        assertEquals(
            PicksResponse(picks.filterKeys { it.round == Round.ROUND_OF_64 }, locked = true),
            shown(leagues.picks(owner, league.id)),
        )
    }

    @Test
    fun `a postponed league's picks stay open until its new start`() {
        val owner = newPlayer()
        val league = create(owner)
        fill(league, 6)
        val picks = upperWinsEverywhere(bracketOf(owner, league))

        // 7 players on 1 March: the start moves to 8 March.
        clock.now = at(START, LocalTime.of(0, 1))

        assertEquals(PicksResponse(picks, locked = false), shown(submit(owner, league, picks)))
    }

    @Test
    fun `another member's picks are hidden until they lock`() {
        val owner = newPlayer(displayName = "Kestrel")
        val league = create(owner)
        val other = fill(league, 7).first()
        val picks = upperWinsEverywhere(bracketOf(other, league))
        shown(submit(other, league, picks))
        val otherKey = memberKey(owner, league, "Player 1")

        assertEquals(PicksOutcome.Hidden, leagues.memberPicks(owner, league.id, otherKey))
        // A player's own, by their own key, at any time.
        assertEquals(PicksResponse(picks, locked = false), shown(leagues.memberPicks(other, league.id, otherKey)))

        clock.now = at(START, LocalTime.of(0, 1))

        assertEquals(PicksResponse(picks, locked = true), shown(leagues.memberPicks(owner, league.id, otherKey)))
        assertEquals(PicksResponse(emptyMap(), locked = true), shown(leagues.memberPicks(other, league.id, memberKey(other, league, "Kestrel"))))
    }

    @Test
    fun `picks only of a member, by a key that is not their account id`() {
        val owner = newPlayer()
        val league = create(owner)
        val other = fill(league, 1).single()
        val otherKey = memberKey(owner, league, "Player 2")
        val stranger = newPlayer()

        assertNotEquals(other.id.toString(), otherKey)
        assertEquals(PicksOutcome.NoSuchMember, leagues.memberPicks(owner, league.id, other.id.toString()))
        assertEquals(PicksOutcome.NoSuchMember, leagues.memberPicks(owner, league.id, UUID.randomUUID().toString()))
        assertEquals(PicksOutcome.NoSuchMember, leagues.memberPicks(owner, league.id, "member-1"))
        assertEquals(PicksOutcome.NotFound, leagues.memberPicks(stranger, league.id, otherKey))
        assertEquals(PicksOutcome.NotFound, leagues.picks(stranger, league.id))
        assertEquals(PicksOutcome.NotFound, submit(stranger, league, emptyMap()))
        assertEquals(PicksOutcome.NotFound, leagues.picks(owner, "league-1"))

        // A member who left is no longer found by their key.
        assertTrue(leagues.leave(other, league.id))
        assertEquals(PicksOutcome.NoSuchMember, leagues.memberPicks(owner, league.id, otherKey))
    }

    @Test
    fun `with no field there is nothing to pick`() {
        val sparseOwner = newPlayer(homeCounty = SPARSE)
        val sparse = create(sparseOwner)

        assertEquals(PicksOutcome.NotReady, submit(sparseOwner, sparse, emptyMap()))
    }

    @Test
    fun `leaving before the start takes the player's picks with them`() {
        val owner = newPlayer()
        val league = create(owner)
        val leaver = fill(league, 1).single()
        shown(submit(leaver, league, upperWinsEverywhere(bracketOf(leaver, league))))

        assertTrue(leagues.leave(leaver, league.id))
        inLeague(leagues.join(leaver, checkNotNull(league.inviteCode)))

        assertEquals(emptyMap<MatchupSlot, String>(), shown(leagues.picks(leaver, league.id)).picks)
    }

    @Test
    fun `a new preview drops the picks for the matchups whose birds changed, and those that depended on them`() {
        val owner = newPlayer(homeCounty = PLENTY)
        val league = create(owner)
        val whole = upperWinsEverywhere(bracketOf(owner, league))
        shown(submit(owner, league, whole))

        // A player from Few Ducks joins: the waterfowl quarter changes, and every matchup it feeds.
        fill(league, 1, homeCounty = FEW_DUCKS)
        val left = shown(leagues.picks(owner, league.id)).picks

        val changed = previewChanges.single().second
        assertTrue(changed.toString(), MatchupSlot(31) in changed && MatchupSlot(62) in changed)
        assertEquals(whole - changed.toSet(), left)
        // The songbird and waterbird quarters, and the semifinal between them, are untouched.
        assertTrue(left.toString(), (0..23).all { MatchupSlot(it) in left })
        assertEquals(whole.getValue(MatchupSlot(60)), left[MatchupSlot(60)])
        assertNull(left[MatchupSlot(62)])
        // What is left is still a sound part of the bracket the player now sees.
        val now = bracketOf(owner, league)
        assertEquals(PicksResponse(left, locked = false), shown(submit(owner, league, left)))
        assertEquals(upperWinsEverywhere(now).filterKeys { it in left }, left)
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

        /** Fieldland's counties (`testFieldPack`): 80 migrants, 66 with only 6 teal, and 40. */
        const val PLENTY = "XX-FD-001"
        const val FEW_DUCKS = "XX-FD-002"
        const val SPARSE = "XX-FD-003"

        /** A well-formed county code that no pack holds. */
        const val OUTSIDE_EVERY_PACK = "XX-QQ-001"

        fun at(date: LocalDate, time: LocalTime = LocalTime.NOON, zone: ZoneId = DENVER): Instant =
            ZonedDateTime.of(date, time, zone).toInstant()
    }
}
