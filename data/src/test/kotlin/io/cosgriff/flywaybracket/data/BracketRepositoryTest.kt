package io.cosgriff.flywaybracket.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.BirdTally
import io.cosgriff.flywaybracket.api.BracketMatchup
import io.cosgriff.flywaybracket.api.BracketResponse
import io.cosgriff.flywaybracket.api.BracketTeam
import io.cosgriff.flywaybracket.api.LeagueDetail
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.LeagueSummary
import io.cosgriff.flywaybracket.api.MatchupResult
import io.cosgriff.flywaybracket.api.MatchupSlot
import io.cosgriff.flywaybracket.api.PicksResponse
import io.cosgriff.flywaybracket.api.RoundResultsResponse
import io.cosgriff.flywaybracket.api.SubmitPicksRequest
import io.cosgriff.flywaybracket.data.account.AccountStore
import io.cosgriff.flywaybracket.data.account.StoredAccount
import io.cosgriff.flywaybracket.data.db.FlywayDatabase
import io.cosgriff.flywaybracket.data.db.OutingEntity
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.data.server.ServerResult
import io.cosgriff.flywaybracket.data.server.serverHttpClient
import io.cosgriff.flywaybracket.domain.bracket.BirdGroup
import io.cosgriff.flywaybracket.domain.bracket.BracketPicks
import io.cosgriff.flywaybracket.domain.bracket.Decision
import io.cosgriff.flywaybracket.domain.bracket.DrawPosition
import io.cosgriff.flywaybracket.domain.bracket.Field
import io.cosgriff.flywaybracket.domain.bracket.PickStatus
import io.cosgriff.flywaybracket.domain.bracket.PicksCheck
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.bracket.Team
import io.cosgriff.flywaybracket.domain.bracket.favourite
import io.cosgriff.flywaybracket.domain.bracket.playBracket
import io.cosgriff.flywaybracket.domain.bracket.testTeams
import io.cosgriff.flywaybracket.domain.league.Score
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.File
import java.net.UnknownHostException
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The league, account, birds and figures are made up. Ktor's mock engine stands in for the server, and an
// in-memory database for the phone's.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BracketRepositoryTest {
    /** A clock the test sets by hand, in UTC. */
    private class TestClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db = Room.inMemoryDatabaseBuilder<FlywayDatabase>(context).build()

    /** Ten days before the season starts. */
    private val clock = TestClock(Instant.parse("2027-02-19T12:00:00Z"))

    // The server's side.
    private var online = true
    private var serverTeams: List<Team> = testTeams()
    private val serverPicks = LinkedHashMap<MatchupSlot, String>()
    private var serverLocked = false
    private var preview = true
    private val winners = HashMap<MatchupSlot, String>()
    private val results = HashMap<Round, RoundResultsResponse>()
    private val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())

    private val accountStore = MemoryAccountStore(StoredAccount(PLAYER_ID, SECRET))
    private val accounts = AccountRepository(
        server = client(),
        store = accountStore,
        settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) {
                File(context.filesDir, "bracket_test.preferences_pb").apply { delete() }
            },
        ),
    )
    private val brackets = BracketRepository(client(), accounts, db.brackets(), db.outings(), clock)

    private val drawn = Field.drawOrder(serverTeams)

    @After
    fun close() {
        db.close()
        scope.cancel()
    }

    private class MemoryAccountStore(var account: StoredAccount?) : AccountStore {
        override suspend fun read() = account

        override suspend fun write(account: StoredAccount) {
            this.account = account
        }

        override suspend fun clear() {
            account = null
        }
    }

    private fun client() = ServerClient(serverHttpClient(MockEngine { request -> answer(request) }), "https://server.example")

    private fun MockRequestHandleScope.answer(request: HttpRequestData) = run {
        if (!online) throw UnknownHostException("server.example")
        requests += request
        val path = request.url.encodedPath
        when {
            path == ApiPaths.league(LEAGUE) -> ok(LeagueDetail.serializer(), leagueDetail())
            path == ApiPaths.bracket(LEAGUE) -> ok(BracketResponse.serializer(), bracketResponse())
            path == ApiPaths.picks(LEAGUE) && request.method == HttpMethod.Get ->
                ok(PicksResponse.serializer(), PicksResponse(serverPicks.toMap(), serverLocked))
            path == ApiPaths.picks(LEAGUE) && request.method == HttpMethod.Put -> {
                val sent = ApiJson.decodeFromString(SubmitPicksRequest.serializer(), (request.body as TextContent).text)
                val check = BracketPicks.check(serverTeams, sent.picks.mapKeys { DrawPosition(it.key.round, it.key.position) })
                when {
                    serverLocked -> refuse(ApiErrorCode.PICKS_LOCKED, HttpStatusCode.Conflict)
                    check != PicksCheck.Consistent -> refuse(ApiErrorCode.PICKS_INCOMPLETE, HttpStatusCode.UnprocessableEntity)
                    else -> {
                        serverPicks.clear()
                        serverPicks.putAll(sent.picks)
                        ok(PicksResponse.serializer(), PicksResponse(serverPicks.toMap(), locked = false))
                    }
                }
            }
            path.startsWith(ApiPaths.league(LEAGUE) + "/rounds/") -> {
                val round = Round.valueOf(path.split('/')[5])
                results[round]?.let { ok(RoundResultsResponse.serializer(), it) }
                    ?: refuse(ApiErrorCode.ROUND_NOT_DECIDED, HttpStatusCode.Conflict)
            }
            else -> refuse(ApiErrorCode.INVALID_REQUEST, HttpStatusCode.NotFound)
        }
    }

    private fun <T> MockRequestHandleScope.ok(serializer: KSerializer<T>, value: T) =
        respond(ApiJson.encodeToString(serializer, value), HttpStatusCode.OK, JSON)

    private fun MockRequestHandleScope.refuse(code: ApiErrorCode, status: HttpStatusCode) =
        respond(ApiJson.encodeToString(ApiError.serializer(), ApiError(code)), status, JSON)

    private fun leagueDetail() = LeagueDetail(
        league = LeagueSummary(id = LEAGUE, name = "Test League", players = 8, capacity = 64, state = LeagueState.BEFORE_SEASON),
        season = Season.SPRING,
        timeZone = "America/Denver",
        seasonStart = SEASON_START,
        seasonEnd = SEASON_START.plusWeeks(6).minusDays(1),
        members = emptyList(),
    )

    private fun bracketResponse(): BracketResponse {
        val order = Field.drawOrder(serverTeams).map { it.commonName }
        return BracketResponse(
            teams = serverTeams.map { BracketTeam(it.commonName, BirdGroup.valueOf(it.quarter), it.seed) },
            matchups = MatchupSlot.all.map { slot ->
                val first = slot.round == Round.ROUND_OF_64
                BracketMatchup(
                    slot = slot,
                    upper = if (first) order[2 * slot.position] else null,
                    lower = if (first) order[2 * slot.position + 1] else null,
                    winner = winners[slot],
                )
            },
            preview = preview,
        )
    }

    private fun at(round: Round, position: Int) = DrawPosition(round, position)

    private fun slot(round: Round, position: Int) = MatchupSlot.of(round, position)

    private fun putRequests() = requests.filter { it.method == HttpMethod.Put }

    private suspend fun current(): LeagueBracket = brackets.bracket(LEAGUE).first()!!

    private fun currentPicks(bracket: LeagueBracket): Map<DrawPosition, String> =
        bracket.view.matchups.filter { it.pick != null }.associate { it.at to it.pick!!.commonName }

    @Test
    fun `refresh keeps the bracket, the dates and the server's picks`() = runBlocking {
        serverPicks[slot(Round.ROUND_OF_64, 0)] = drawn[0].commonName

        assertNull(brackets.bracket(LEAGUE).first())
        assertEquals(ServerResult.Ok(Unit), brackets.refresh(LEAGUE))

        val bracket = current()
        assertTrue(bracket.preview)
        assertEquals(SEASON_START, bracket.calendar.startDate)
        assertEquals(ZoneId.of("America/Denver"), bracket.timeZone)
        assertEquals(mapOf(at(Round.ROUND_OF_64, 0) to drawn[0].commonName), currentPicks(bracket))
        assertFalse(bracket.picksUnsent)
        assertFalse(bracket.picksLocked(clock.now))
        assertNull(bracket.notice)
        assertEquals(64, bracket.view.teams.size)
    }

    @Test
    fun `a pick is saved on the phone and sent, and the picks are all that is sent`() = runBlocking {
        brackets.refresh(LEAGUE)

        val picked = brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 3), drawn[7].commonName)

        assertEquals(PickResult.Saved(ServerResult.Ok(Unit)), picked)
        assertEquals(mapOf(slot(Round.ROUND_OF_64, 3) to drawn[7].commonName), serverPicks)
        assertFalse(current().picksUnsent)
        val body = (putRequests().single().body as TextContent).text
        assertEquals("""{"picks":{"3":"${drawn[7].commonName}"}}""", body)
    }

    @Test
    fun `picks made offline are kept on the phone and sent with the next refresh`() = runBlocking {
        brackets.refresh(LEAGUE)
        online = false

        val first = brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 0), drawn[0].commonName)
        brackets.pick(LEAGUE, at(Round.ROUND_OF_32, 0), drawn[0].commonName)

        assertEquals(PickResult.Saved(ServerResult.Failed(ServerProblem.NoNetwork)), first)
        val offline = current()
        assertTrue(offline.picksUnsent)
        assertEquals(PickStatus.ALIVE, offline.view.matchup(at(Round.ROUND_OF_32, 0)).pickStatus)
        assertEquals(emptyMap<MatchupSlot, String>(), serverPicks)

        online = true
        val before = requests.size
        assertEquals(ServerResult.Ok(Unit), brackets.refresh(LEAGUE))

        assertEquals(
            mapOf(slot(Round.ROUND_OF_64, 0) to drawn[0].commonName, slot(Round.ROUND_OF_32, 0) to drawn[0].commonName),
            serverPicks,
        )
        assertFalse(current().picksUnsent)
        // The picks went once, with the refresh, and the answer to them was taken as the server's picks.
        assertEquals(1, putRequests().size)
        assertTrue(requests.drop(before).none { it.method == HttpMethod.Get && it.url.encodedPath == ApiPaths.picks(LEAGUE) })
    }

    @Test
    fun `a lock that arrives while offline wins over the phone's unsent picks, and says so`() = runBlocking {
        serverPicks[slot(Round.ROUND_OF_64, 0)] = drawn[0].commonName
        brackets.refresh(LEAGUE)
        online = false
        brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 1), drawn[2].commonName)
        assertTrue(current().picksUnsent)

        // The server locks picks while the phone is offline; the phone's clock is still before the start.
        serverLocked = true
        online = true
        val refreshed = brackets.refresh(LEAGUE)

        assertEquals(ServerResult.Failed(ServerProblem.Refused(ApiErrorCode.PICKS_LOCKED)), refreshed)
        val locked = current()
        assertEquals(PicksNotice.LOCKED_BEFORE_SENT, locked.notice)
        assertTrue(locked.lockedByServer)
        assertTrue(locked.picksLocked(clock.now))
        assertFalse(locked.picksUnsent)
        assertEquals(mapOf(at(Round.ROUND_OF_64, 0) to drawn[0].commonName), currentPicks(locked))
        assertEquals(PickResult.Locked, brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 2), drawn[4].commonName))

        brackets.dismissNotice(LEAGUE)
        assertNull(current().notice)
    }

    @Test
    fun `picks lock at midnight on the first Monday in the league's zone`() = runBlocking {
        brackets.refresh(LEAGUE)

        // Midnight on 1 March 2027 in Denver is 07:00 UTC.
        clock.now = Instant.parse("2027-03-01T06:59:59Z")
        assertEquals(PickResult.Saved(ServerResult.Ok(Unit)), brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 0), drawn[1].commonName))
        clock.now = Instant.parse("2027-03-01T07:00:00Z")
        val sentBefore = requests.size

        assertEquals(PickResult.Locked, brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 1), drawn[2].commonName))
        assertEquals(sentBefore, requests.size)
        assertEquals(1, currentPicks(current()).size)
    }

    @Test
    fun `changing an early pick clears the later picks that depended on it, on the phone and the server`() = runBlocking {
        brackets.refresh(LEAGUE)
        brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 0), drawn[0].commonName)
        brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 1), drawn[2].commonName)
        brackets.pick(LEAGUE, at(Round.ROUND_OF_32, 0), drawn[0].commonName)

        brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 0), drawn[1].commonName)

        val expected = mapOf(at(Round.ROUND_OF_64, 0) to drawn[1].commonName, at(Round.ROUND_OF_64, 1) to drawn[2].commonName)
        assertEquals(expected, currentPicks(current()))
        assertEquals(expected.mapKeys { slot(it.key.round, it.key.position) }, serverPicks)
    }

    @Test
    fun `a pick that does not fit the others is refused and nothing changes`() = runBlocking {
        brackets.refresh(LEAGUE)

        val picked = brackets.pick(LEAGUE, at(Round.ROUND_OF_32, 0), drawn[0].commonName)

        assertEquals(PickResult.DoesNotFit(PicksCheck.NotPickedToReach(at(Round.ROUND_OF_32, 0))), picked)
        assertEquals(emptyMap<DrawPosition, String>(), currentPicks(current()))
        assertEquals(emptyList<HttpRequestData>(), putRequests())
    }

    @Test
    fun `with no bracket on the phone a pick asks for a refresh`() = runBlocking {
        assertEquals(PickResult.NoBracket, brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 0), drawn[0].commonName))
    }

    @Test
    fun `a preview seeded again drops the unsent picks whose birds changed, and says so`() = runBlocking {
        brackets.refresh(LEAGUE)
        online = false
        brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 0), drawn[0].commonName)
        brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 1), drawn[2].commonName)
        brackets.pick(LEAGUE, at(Round.ROUND_OF_32, 0), drawn[2].commonName)
        // A member joins: the top quarter's 16 seed, drawn second, is now another bird.
        serverTeams = serverTeams.map { if (it == drawn[1]) it.copy(commonName = "Test Newcomer") else it }
        online = true

        assertEquals(ServerResult.Ok(Unit), brackets.refresh(LEAGUE))

        // The first matchup's birds changed, and so did those of every later matchup it feeds: the picks there
        // go. The second matchup's pick stays.
        val bracket = current()
        assertEquals(PicksNotice.BRACKET_CHANGED, bracket.notice)
        assertEquals(mapOf(at(Round.ROUND_OF_64, 1) to drawn[2].commonName), currentPicks(bracket))
        assertEquals(mapOf(slot(Round.ROUND_OF_64, 1) to drawn[2].commonName), serverPicks)
        assertTrue(bracket.view.teams.any { it.commonName == "Test Newcomer" })
    }

    @Test
    fun `picks the server no longer takes are replaced by the server's bracket and picks`() = runBlocking {
        brackets.refresh(LEAGUE)
        online = false
        brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 0), drawn[0].commonName)
        // The preview changes after the phone's last fetch, and the phone sends before it fetches again.
        serverTeams = serverTeams.map { if (it == drawn[0]) it.copy(commonName = "Test Newcomer") else it }
        online = true

        val sent = brackets.sendPicks(LEAGUE)

        assertEquals(ServerResult.Failed(ServerProblem.Refused(ApiErrorCode.PICKS_INCOMPLETE)), sent)
        val bracket = current()
        assertEquals(PicksNotice.BRACKET_CHANGED, bracket.notice)
        assertFalse(bracket.picksUnsent)
        assertEquals(emptyMap<DrawPosition, String>(), currentPicks(bracket))
        assertTrue(bracket.view.teams.any { it.commonName == "Test Newcomer" })
    }

    @Test
    fun `the results of each decided round are fetched once and shown`() = runBlocking {
        val favourites = playBracket(serverTeams) { _, upper, lower -> favourite(upper, lower) }
        val firstRound = favourites.filterKeys { it.round == Round.ROUND_OF_64 }
        firstRound.forEach { (at, team) -> serverPicks[slot(at.round, at.position)] = team.commonName }
        firstRound.forEach { (at, team) -> winners[slot(at.round, at.position)] = team.commonName }
        results[Round.ROUND_OF_64] = RoundResultsResponse(
            Round.ROUND_OF_64,
            firstRound.map { (at, team) ->
                val upper = drawn[2 * at.position]
                MatchupResult(
                    slot = slot(at.round, at.position),
                    upper = BirdTally(upper.commonName, eligible = 9, logged = if (upper == team) 5 else 2),
                    lower = BirdTally(drawn[2 * at.position + 1].commonName, eligible = 9, logged = if (upper == team) 2 else 5),
                    winner = team.commonName,
                    reason = Decision.Reason.HIGHER_SHARE,
                    roundPoints = 1,
                    upsetBonus = 0,
                )
            },
        )
        preview = false
        serverLocked = true
        clock.now = Instant.parse("2027-03-09T12:00:00Z")

        brackets.refresh(LEAGUE)
        brackets.refresh(LEAGUE)

        val bracket = current()
        assertFalse(bracket.preview)
        assertEquals(32, bracket.view.decided)
        assertEquals(Score(points = 32, rightPicks = 32), bracket.view.score)
        assertEquals(drawn[0], bracket.view.matchup(at(Round.ROUND_OF_64, 0)).winner)
        assertEquals(Round.ROUND_OF_32, bracket.roundOn(clock.now))
        assertEquals(1, requests.count { it.url.encodedPath == ApiPaths.roundResults(LEAGUE, Round.ROUND_OF_64) })
    }

    @Test
    fun `picks not yet seen are the alive picks of this round the player has not logged`() = runBlocking {
        brackets.refresh(LEAGUE)
        brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 0), drawn[0].commonName)
        brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 1), drawn[2].commonName)
        brackets.pick(LEAGUE, at(Round.ROUND_OF_64, 2), drawn[4].commonName)
        // Before the season there is no round, so nothing to see.
        assertEquals(emptyList<Team>(), brackets.picksNotYetSeen(LEAGUE).first())

        // Wednesday of round 1. One picked bird was logged on Tuesday; another the Sunday before the round.
        clock.now = Instant.parse("2027-03-03T18:00:00Z")
        val outing = db.outings().insert(OutingEntity(placeName = "Test Pond", countyCode = "US-ZZ-001", startedAt = 0, endedAt = null))
        db.outings().addOrBump(outing, drawn[0].commonName, at = Instant.parse("2027-03-02T15:00:00Z").toEpochMilli())
        db.outings().addOrBump(outing, drawn[2].commonName, at = Instant.parse("2027-02-28T15:00:00Z").toEpochMilli())

        assertEquals(listOf(drawn[2], drawn[4]), brackets.picksNotYetSeen(LEAGUE).first())
    }

    @Test
    fun `with no account nothing is asked or sent`() = runBlocking {
        accountStore.account = null

        val noAccount = ServerResult.Failed(ServerProblem.Refused(ApiErrorCode.UNAUTHORIZED))
        assertEquals(noAccount, brackets.refresh(LEAGUE))
        assertEquals(noAccount, brackets.sendPicks(LEAGUE))
        assertEquals(emptyList<HttpRequestData>(), requests)
    }

    private companion object {
        const val LEAGUE = "test-league-1"
        const val PLAYER_ID = "00000000-0000-4000-8000-000000000001"
        const val SECRET = "test-secret-not-real"
        val SEASON_START: LocalDate = LocalDate.of(2027, 3, 1)
        val JSON = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
