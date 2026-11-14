package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.api.BirdTally
import io.cosgriff.flywaybracket.api.BracketResponse
import io.cosgriff.flywaybracket.api.CreateLeagueRequest
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.LeagueSummary
import io.cosgriff.flywaybracket.api.MatchupResult
import io.cosgriff.flywaybracket.api.MatchupSlot
import io.cosgriff.flywaybracket.api.RoundResultsResponse
import io.cosgriff.flywaybracket.api.Standing
import io.cosgriff.flywaybracket.api.SubmitPicksRequest
import io.cosgriff.flywaybracket.domain.bracket.BirdGroup
import io.cosgriff.flywaybracket.domain.bracket.Decision.Reason
import io.cosgriff.flywaybracket.domain.bracket.MatchupRule
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendarResult
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.account.SignedInPlayer
import io.cosgriff.flywaybracket.server.pack.testCharts
import io.cosgriff.flywaybracket.server.sighting.SightingStore
import io.cosgriff.flywaybracket.server.sighting.StoredSighting
import org.junit.Assert.assertEquals
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

/**
 * Deciding rounds, scoring picks and ranking the league (#37), against each set of stores:
 * [InMemoryRoundResultsTest] needs nothing, [PostgresRoundResultsTest] a real Postgres. Leagues of 8 or more
 * made-up players, in the made-up counties of Fieldland, with made-up sightings.
 */
abstract class RoundResultsTest {
    /** A store holding no player, no league and no sighting. */
    protected abstract fun players(): PlayerStore

    /** The leagues of [players]' players, holding none, reading the sightings [sightings] holds. */
    protected abstract fun leagueStore(players: PlayerStore, sightings: SightingStore): LeagueStore

    protected abstract fun sightingStore(players: PlayerStore): SightingStore

    private val playerStore by lazy { players() }
    private val sightingStore by lazy { sightingStore(playerStore) }

    /** Wednesday 17 February 2027, noon in Denver. */
    private val clock = HandClock(at(LocalDate.of(2027, 2, 17)))
    private val charts = testCharts()
    protected val leagues by lazy { Leagues(leagueStore(playerStore, sightingStore), charts, clock) }
    private var made = 0

    private fun newPlayer(homeCounty: String): SignedInPlayer {
        val id = UUID.randomUUID()
        playerStore.create(id, "made-up-hash")
        playerStore.update(id, "Player ${++made}", homeCounty)
        return SignedInPlayer(id)
    }

    /** A league starting on [START] in Denver, with a player from each of [homes], in that order of joining. */
    private fun league(homes: List<String>): Pair<LeagueSummary, List<SignedInPlayer>> {
        val players = homes.map(::newPlayer)
        val created = leagues.create(players.first(), CreateLeagueRequest("Thursday Walkers", false, DENVER.id, START))
        val league = (created as LeagueOutcome.InLeague).league
        players.drop(1).forEach { assertTrue(leagues.join(it, checkNotNull(league.inviteCode)) is LeagueOutcome.InLeague) }
        return league to players
    }

    private fun bracketOf(player: SignedInPlayer, league: LeagueSummary): BracketResponse =
        (leagues.bracket(player, league.id) as BracketOutcome.Ready).bracket

    private fun pick(player: SignedInPlayer, league: LeagueSummary, picks: Map<MatchupSlot, String>) {
        assertTrue(leagues.submitPicks(player, league.id, SubmitPicksRequest(picks)) is PicksOutcome.Shown)
    }

    private fun saw(player: SignedInPlayer, commonName: String, county: String, date: LocalDate) {
        assertTrue(sightingStore.add(player.id, listOf(StoredSighting(UUID.randomUUID(), commonName, county, date))))
    }

    private fun results(player: SignedInPlayer, league: LeagueSummary, round: Round): RoundResultsResponse {
        val outcome = leagues.roundResults(player, league.id, round)
        assertTrue(outcome.toString(), outcome is ResultsOutcome.Shown)
        return (outcome as ResultsOutcome.Shown).results
    }

    private fun standings(player: SignedInPlayer, league: LeagueSummary): List<Standing> =
        checkNotNull(leagues.standings(player, league.id)).standings

    private fun RoundResultsResponse.at(slot: Int): MatchupResult = matchups.single { it.slot == MatchupSlot(slot) }

    private fun BracketResponse.seedOf(commonName: String): Int = teams.single { it.commonName == commonName }.seed

    /** The two birds of a first-round matchup: the better seed, then the worse. */
    private fun BracketResponse.favoriteAndUnderdog(slot: Int): Pair<String, String> {
        val matchup = matchups[slot]
        val birds = listOf(checkNotNull(matchup.upper), checkNotNull(matchup.lower)).sortedBy { seedOf(it) }
        return birds[0] to birds[1]
    }

    /** Moves the clock to the moment [round] is decided, plus [later]. */
    private fun decide(round: Round, later: Duration = Duration.ZERO) {
        clock.now = ZonedDateTime.of(CALENDAR.decidedAt(round), DENVER).toInstant().plus(later)
    }

    // Deciding a matchup

    @Test
    fun `a clear win goes to the bird more of its eligible players logged, and a called upset earns the seed gap`() {
        val (league, players) = league(List(8) { PLENTY })
        val bracket = bracketOf(players[0], league)
        val (favorite, underdog) = bracket.favoriteAndUnderdog(0)
        pick(players[0], league, mapOf(MatchupSlot(0) to underdog))
        pick(players[1], league, mapOf(MatchupSlot(0) to favorite))
        clock.now = at(START.plusDays(1))
        // Three of the eight logged the underdog during the round, one the favorite.
        players.take(3).forEach { saw(it, underdog, PLENTY, START.plusDays(2)) }
        saw(players[3], favorite, PLENTY, START.plusDays(6))

        decide(Round.ROUND_OF_64)

        val gap = bracket.seedOf(underdog) - bracket.seedOf(favorite)
        val result = results(players[0], league, Round.ROUND_OF_64).at(0)
        assertEquals(underdog, result.winner)
        assertEquals(Reason.HIGHER_SHARE, result.reason)
        assertEquals(setOf(BirdTally(underdog, 8, 3), BirdTally(favorite, 8, 1)), setOf(result.upper, result.lower))
        assertEquals(1 to gap, result.roundPoints to result.upsetBonus)
        assertEquals(0 to 0, results(players[1], league, Round.ROUND_OF_64).at(0).let { it.roundPoints to it.upsetBonus })

        val table = standings(players[1], league)
        assertEquals(Standing("Player 1", points = 1 + gap, rank = 1, memberKey = table[0].memberKey, rightPicks = 1), table[0])
        assertEquals(List(7) { 2 }, table.drop(1).map { it.rank })
        assertEquals("Player 2", table.single { it.you }.displayName)
    }

    @Test
    fun `equal shares go to the better seed`() {
        val (league, players) = league(List(8) { PLENTY })
        val bracket = bracketOf(players[0], league)
        val (favorite, underdog) = bracket.favoriteAndUnderdog(1)
        clock.now = at(START.plusDays(1))
        players.take(2).forEach { saw(it, favorite, PLENTY, START) }
        players.drop(2).take(2).forEach { saw(it, underdog, PLENTY, START.plusDays(3)) }

        decide(Round.ROUND_OF_64)

        val result = results(players[0], league, Round.ROUND_OF_64).at(1)
        assertEquals(favorite, result.winner)
        assertEquals(Reason.TIED_BETTER_SEED, result.reason)
        assertEquals(setOf(BirdTally(favorite, 8, 2), BirdTally(underdog, 8, 2)), setOf(result.upper, result.lower))
        // No sighting at all is a tie too.
        assertEquals(Reason.TIED_BETTER_SEED, results(players[0], league, Round.ROUND_OF_64).at(2).reason)
    }

    /**
     * Two players from Plenty, where every teal is expected, and six from Few Ducks, where only six are: a
     * first-round waterfowl matchup between two teal Few Ducks never sees.
     */
    private fun splitLeague(): Triple<LeagueSummary, List<SignedInPlayer>, Int> {
        val (league, players) = league(listOf(PLENTY, PLENTY) + List(6) { FEW_DUCKS })
        val bracket = bracketOf(players[0], league)
        val fewDucks = checkNotNull(charts.chartFor(FEW_DUCKS))
        val bins = CALENDAR.bins(Round.ROUND_OF_64)
        val slot = MatchupSlot.all.filter { it.round == Round.ROUND_OF_64 }.first { slot ->
            val matchup = bracket.matchups[slot.index]
            listOf(matchup.upper, matchup.lower).all { bird ->
                bracket.teams.single { it.commonName == bird }.quarter == BirdGroup.WATERFOWL &&
                    !MatchupRule.isEligible(checkNotNull(bird), bins, fewDucks)
            }
        }
        return Triple(league, players, slot.index)
    }

    @Test
    fun `with too few eligible players for either bird the better seed advances`() {
        val (league, players, slot) = splitLeague()
        val bracket = bracketOf(players[0], league)
        val (favorite, underdog) = bracket.favoriteAndUnderdog(slot)
        clock.now = at(START.plusDays(1))
        // One of the two who could see it logged the underdog: 1 of 2 is too few to count.
        saw(players[0], underdog, PLENTY, START.plusDays(1))
        // A player from Few Ducks logged it at home, where it is not expected: not eligible, so not counted.
        saw(players[5], underdog, FEW_DUCKS, START.plusDays(1))

        decide(Round.ROUND_OF_64)

        val result = results(players[0], league, Round.ROUND_OF_64).at(slot)
        assertEquals(favorite, result.winner)
        assertEquals(Reason.TOO_FEW_ELIGIBLE, result.reason)
        assertEquals(setOf(BirdTally(underdog, 2, 1), BirdTally(favorite, 2, 0)), setOf(result.upper, result.lower))
    }

    @Test
    fun `a traveler becomes eligible through a sighting in another county`() {
        val (league, players, slot) = splitLeague()
        val bracket = bracketOf(players[0], league)
        val (favorite, underdog) = bracket.favoriteAndUnderdog(slot)
        clock.now = at(START.plusDays(1))
        // A player from Few Ducks went to Plenty during the round and logged the underdog there.
        saw(players[4], underdog, PLENTY, START.plusDays(4))
        // A trip before the round does not count for it.
        saw(players[5], underdog, PLENTY, START.minusDays(1))

        decide(Round.ROUND_OF_64)

        val result = results(players[0], league, Round.ROUND_OF_64).at(slot)
        assertEquals(underdog, result.winner)
        assertEquals(Reason.HIGHER_SHARE, result.reason)
        assertEquals(setOf(BirdTally(underdog, 3, 1), BirdTally(favorite, 3, 0)), setOf(result.upper, result.lower))
    }

    // When a round is decided

    @Test
    fun `a round is decided 12 hours after its Sunday ends, from the sightings stored by then`() {
        val (league, players) = league(List(8) { PLENTY })
        val bracket = bracketOf(players[0], league)
        val (favorite, underdog) = bracket.favoriteAndUnderdog(0)
        // Monday 8 March, 11:59 in Denver: the round's Sunday has ended, but it is not decided yet.
        decide(Round.ROUND_OF_64, Duration.ofMinutes(-1))
        assertEquals(ResultsOutcome.NotDecided, leagues.roundResults(players[0], league.id, Round.ROUND_OF_64))
        // A phone that was offline sends Sunday's sighting in time; one dated Monday is the next round's.
        saw(players[0], underdog, PLENTY, START.plusDays(6))
        saw(players[1], underdog, PLENTY, START.plusDays(7))

        decide(Round.ROUND_OF_64)

        val result = results(players[0], league, Round.ROUND_OF_64).at(0)
        assertEquals(underdog, result.winner)
        assertEquals(setOf(BirdTally(underdog, 8, 1), BirdTally(favorite, 8, 0)), setOf(result.upper, result.lower))
        assertEquals(32, results(players[0], league, Round.ROUND_OF_64).matchups.size)
        assertEquals(ResultsOutcome.NotDecided, leagues.roundResults(players[0], league.id, Round.ROUND_OF_32))
        // The winners meet in the next round's matchups.
        val next = bracketOf(players[0], league)
        assertEquals(underdog, next.matchups[0].winner)
        assertEquals(underdog, next.matchups[32].upper)
        assertEquals(next.matchups[1].winner, next.matchups[32].lower)
        assertNull(next.matchups[32].winner)
        assertNull(next.matchups[48].upper)
    }

    @Test
    fun `settling again, by the job or a read, decides nothing twice, and a late sighting changes nothing`() {
        val (league, players) = league(List(8) { PLENTY })
        val (favorite, underdog) = bracketOf(players[0], league).favoriteAndUnderdog(0)
        clock.now = at(START.plusDays(1))
        saw(players[0], underdog, PLENTY, START.plusDays(1))
        decide(Round.ROUND_OF_64)
        val job = SeasonStartJob(leagues)

        job.runOnce()
        job.runOnce()
        val first = results(players[0], league, Round.ROUND_OF_64)
        // Two more players' sightings of the favorite, dated in the round, arrive after it is decided.
        decide(Round.ROUND_OF_64, Duration.ofHours(1))
        saw(players[1], favorite, PLENTY, START.plusDays(5))
        saw(players[2], favorite, PLENTY, START.plusDays(5))
        job.runOnce()
        leagues.settle(UUID.fromString(league.id))

        assertEquals(first, results(players[0], league, Round.ROUND_OF_64))
        assertEquals(underdog, first.at(0).winner)
        assertEquals(setOf(BirdTally(underdog, 8, 1), BirdTally(favorite, 8, 0)), setOf(first.at(0).upper, first.at(0).lower))
    }

    @Test
    fun `a home county changed during a round counts only from the next round`() {
        val (league, players, slot) = splitLeague()
        val (favorite, underdog) = bracketOf(players[0], league).favoriteAndUnderdog(slot)
        // The round has begun, and a read settles the league.
        clock.now = at(START.plusDays(1))
        leagues.standings(players[0], league.id)
        // A player from Few Ducks moves to Plenty mid-round.
        playerStore.update(players[4].id, null, PLENTY)

        decide(Round.ROUND_OF_64)

        // Round 1 still reads their home as Few Ducks: two eligible, not three.
        val result = results(players[0], league, Round.ROUND_OF_64).at(slot)
        assertEquals(Reason.TOO_FEW_ELIGIBLE, result.reason)
        assertEquals(setOf(BirdTally(underdog, 2, 0), BirdTally(favorite, 2, 0)), setOf(result.upper, result.lower))

        decide(Round.ROUND_OF_32)

        // Round 2 began after the move: a bird Few Ducks never sees now has them among its eligible.
        val fewDucks = checkNotNull(charts.chartFor(FEW_DUCKS))
        val bins = CALENDAR.bins(Round.ROUND_OF_32)
        val plentyOnly = results(players[0], league, Round.ROUND_OF_32).matchups
            .flatMap { listOf(it.upper, it.lower) }
            .first { tally -> !MatchupRule.isEligible(tally.commonName, bins, fewDucks) }
        assertEquals(3, plentyOnly.eligible)
    }

    // Leaving

    @Test
    fun `a player who left counts in the rounds decided before they left, and in none after`() {
        val (league, players) = league(List(9) { PLENTY })
        val bracket = bracketOf(players[0], league)
        val (_, underdog) = bracket.favoriteAndUnderdog(0)
        val everyUpper = MatchupSlot.all.filter { it.round == Round.ROUND_OF_64 }.associateWith { checkNotNull(bracket.matchups[it.index].upper) }
        players.forEach { pick(it, league, everyUpper) }
        clock.now = at(START.plusDays(1))
        val (early, late) = players[7] to players[8]
        saw(early, underdog, PLENTY, START.plusDays(1))
        saw(late, underdog, PLENTY, START.plusDays(1))

        // One leaves on Monday morning, after the round's Sunday but before it is decided: not counted in it.
        decide(Round.ROUND_OF_64, Duration.ofHours(-1))
        assertTrue(leagues.leave(early, league.id))
        decide(Round.ROUND_OF_64)
        val round1 = results(players[0], league, Round.ROUND_OF_64).at(0)
        assertEquals(BirdTally(underdog, 8, 1), listOf(round1.upper, round1.lower).single { it.commonName == underdog })

        // The other leaves during round 2, having logged a bird of it: the decided round keeps them.
        val next = bracketOf(players[0], league).matchups[32]
        saw(late, checkNotNull(next.upper), PLENTY, START.plusDays(8))
        clock.now = at(START.plusDays(9))
        assertTrue(leagues.leave(late, league.id))
        assertEquals(round1, results(players[0], league, Round.ROUND_OF_64).at(0))
        decide(Round.ROUND_OF_32)

        val round2 = results(players[0], league, Round.ROUND_OF_32).at(32)
        assertEquals(BirdTally(checkNotNull(next.upper), 7, 0), round2.upper)
        assertEquals(ResultsOutcome.NotFound, leagues.roundResults(late, league.id, Round.ROUND_OF_64))
        // Neither is in the table, and their picks score for no one.
        val table = standings(players[0], league)
        assertEquals((1..7).map { "Player $it" }, table.map { it.displayName }.sorted())
    }

    // A whole season

    @Test
    fun `a whole season through the final`() {
        val (league, players) = league(List(8) { PLENTY })
        val bracket = bracketOf(players[0], league)
        val favorites = favoritesEverywhere(bracket)
        val uppers = upperEverywhere(bracket)
        pick(players[0], league, favorites)
        pick(players[1], league, uppers)
        // Each week one player logs the bird drawn below in the round's first matchup, so it wins.
        clock.now = at(START.plusDays(1))

        for (round in Round.entries) {
            val first = MatchupSlot.all.first { it.round == round }
            val lower = checkNotNull(bracketOf(players[0], league).matchups[first.index].lower)
            saw(players[round.ordinal], lower, PLENTY, CALENDAR.roundDates(round).start.plusDays(2))
            decide(round, Duration.ofMinutes(-1))
            assertEquals(LeagueState.IN_SEASON, leagues.mine(players[0]).single().state)
            decide(round)
            assertEquals(lower, results(players[0], league, round).at(first.index).winner)
        }

        assertEquals(LeagueState.FINISHED, leagues.mine(players[0]).single().state)
        val final = bracketOf(players[0], league)
        assertTrue(final.matchups.all { it.winner != null })
        // Every result, scored by hand: the round's points for each right pick, and the seed gap for an upset.
        val all = Round.entries.flatMap { results(players[0], league, it).matchups }
        assertEquals(63, all.size)
        fun expected(picks: Map<MatchupSlot, String>) = all.filter { picks[it.slot] == it.winner }.sumOf { result ->
            val loser = if (result.winner == result.upper.commonName) result.lower.commonName else result.upper.commonName
            result.slot.round.points + (bracket.seedOf(result.winner) - bracket.seedOf(loser)).coerceAtLeast(0)
        }
        val table = standings(players[0], league)
        val byName = table.associateBy { it.displayName }
        assertEquals(expected(favorites), byName.getValue("Player 1").points)
        assertEquals(expected(uppers), byName.getValue("Player 2").points)
        assertEquals(all.count { favorites[it.slot] == it.winner }, byName.getValue("Player 1").rightPicks)
        // The results show the asking player what each pick earned; together, their points.
        assertEquals(byName.getValue("Player 1").points, all.sumOf { it.roundPoints + it.upsetBonus })
        // Best first; the six with no picks share the last rank, in the order they joined.
        assertEquals(table.sortedByDescending { it.points }.map { it.points }, table.map { it.points })
        assertEquals((3..8).map { "Player $it" }, table.drop(2).map { it.displayName })
        assertEquals(List(6) { 3 }, table.drop(2).map { it.rank })
        assertEquals(table, checkNotNull(leagues.detail(players[0], league.id)).members)
    }

    /** Picks of the better seed in every matchup, as a tie between two seeds alike goes by name. */
    private fun favoritesEverywhere(bracket: BracketResponse): Map<MatchupSlot, String> {
        val better = compareBy<String>({ bracket.seedOf(it) }, { it })
        val picks = sortedMapOf<MatchupSlot, String>()
        MatchupSlot.all.forEach { slot ->
            picks[slot] = if (slot.round == Round.ROUND_OF_64) {
                listOf(checkNotNull(bracket.matchups[slot.index].upper), checkNotNull(bracket.matchups[slot.index].lower)).minWith(better)
            } else {
                val earlier = Round.entries[slot.round.ordinal - 1]
                listOf(picks.getValue(MatchupSlot.of(earlier, 2 * slot.position)), picks.getValue(MatchupSlot.of(earlier, 2 * slot.position + 1)))
                    .minWith(better)
            }
        }
        return picks
    }

    private fun upperEverywhere(bracket: BracketResponse): Map<MatchupSlot, String> {
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

    protected companion object {
        val DENVER: ZoneId = ZoneId.of("America/Denver")

        /** Monday 1 March 2027. */
        val START: LocalDate = LocalDate.of(2027, 3, 1)
        val CALENDAR: SeasonCalendar = (SeasonCalendar.of(START) as SeasonCalendarResult.Valid).calendar

        /** Fieldland's counties (`testFieldPack`): 20 teal in Plenty, only the six commonest in Few Ducks. */
        const val PLENTY = "XX-FD-001"
        const val FEW_DUCKS = "XX-FD-002"

        fun at(date: LocalDate, time: LocalTime = LocalTime.NOON): Instant = ZonedDateTime.of(date, time, DENVER).toInstant()
    }
}
