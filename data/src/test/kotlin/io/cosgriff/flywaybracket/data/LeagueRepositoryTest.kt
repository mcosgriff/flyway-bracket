package io.cosgriff.flywaybracket.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.CreateLeagueRequest
import io.cosgriff.flywaybracket.api.JoinLeagueRequest
import io.cosgriff.flywaybracket.api.LeagueDetail
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.LeagueSummary
import io.cosgriff.flywaybracket.api.ProfileResponse
import io.cosgriff.flywaybracket.api.Standing
import io.cosgriff.flywaybracket.data.account.AccountStore
import io.cosgriff.flywaybracket.data.account.StoredAccount
import io.cosgriff.flywaybracket.data.db.FlywayDatabase
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.data.server.ServerResult
import io.cosgriff.flywaybracket.data.server.serverHttpClient
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.league.CloseReason
import io.cosgriff.flywaybracket.domain.league.LeaguePhase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The league, its players, the account and the dates are made up. Ktor's mock engine stands in for the
// server, and an in-memory database for the phone's.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LeagueRepositoryTest {
    private class TestClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = Clock.fixed(now, zone)

        override fun instant(): Instant = now
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

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val db = Room.inMemoryDatabaseBuilder<FlywayDatabase>(context).build()

    /** Wednesday 17 February 2027, noon UTC. */
    private val clock = TestClock(Instant.parse("2027-02-17T12:00:00Z"))

    // The server's side.
    private var online = true
    private var serverLeague: LeagueSummary? = null
    private var members: List<Standing> = emptyList()
    private var refuseWith: Pair<ApiErrorCode, HttpStatusCode>? = null
    private val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())

    private val accountStore = MemoryAccountStore(StoredAccount(PLAYER_ID, SECRET))
    private val accounts = AccountRepository(
        server = client(),
        store = accountStore,
        settings = settings("league_test"),
    )
    private val leagues = LeagueRepository(client(), accounts, db.leagues(), clock)

    @After
    fun close() {
        db.close()
        scope.cancel()
    }

    private fun client() = ServerClient(serverHttpClient(MockEngine { request -> answer(request) }), "https://server.example")

    private fun MockRequestHandleScope.answer(request: HttpRequestData) = run {
        if (!online) throw UnknownHostException("server.example")
        requests += request
        refuseWith?.let { (code, status) -> return@run refuse(code, status) }
        val path = request.url.encodedPath
        when {
            path == ApiPaths.ACCOUNT -> ok(ProfileResponse.serializer(), ProfileResponse(displayName = "Wren Example", league = serverLeague))
            path == ApiPaths.LEAGUES && request.method == HttpMethod.Post -> {
                val sent = ApiJson.decodeFromString(CreateLeagueRequest.serializer(), (request.body as TextContent).text)
                serverLeague = summary(name = sent.name)
                members = listOf(standing("Wren Example", you = true))
                created(LeagueSummary.serializer(), serverLeague!!)
            }
            path == ApiPaths.JOIN_LEAGUE || path == ApiPaths.AUTO_JOIN_LEAGUE -> {
                serverLeague = summary()
                members = listOf(standing("Sora Example"), standing("Wren Example", you = true))
                ok(LeagueSummary.serializer(), serverLeague!!)
            }
            path == ApiPaths.membership(LEAGUE) -> {
                serverLeague = null
                respond("", HttpStatusCode.NoContent)
            }
            path == ApiPaths.league(LEAGUE) && serverLeague != null -> ok(LeagueDetail.serializer(), detail(serverLeague!!))
            else -> refuse(ApiErrorCode.LEAGUE_NOT_FOUND, HttpStatusCode.NotFound)
        }
    }

    private fun summary(name: String = "Test League", state: LeagueState = LeagueState.BEFORE_SEASON, closedBecause: CloseReason? = null) =
        LeagueSummary(id = LEAGUE, name = name, inviteCode = CODE, players = 2, capacity = 64, state = state, closedBecause = closedBecause)

    private fun standing(name: String, you: Boolean = false, points: Int = 0) =
        Standing(displayName = name, points = points, rank = 1, memberKey = "key-$name", you = you)

    private fun detail(league: LeagueSummary) = LeagueDetail(
        league = league,
        season = Season.SPRING,
        timeZone = "America/Denver",
        seasonStart = SEASON_START,
        seasonEnd = SEASON_START.plusWeeks(6).minusDays(1),
        members = members,
    )

    private fun <T> MockRequestHandleScope.ok(serializer: KSerializer<T>, value: T) =
        respond(ApiJson.encodeToString(serializer, value), HttpStatusCode.OK, JSON)

    private fun <T> MockRequestHandleScope.created(serializer: KSerializer<T>, value: T) =
        respond(ApiJson.encodeToString(serializer, value), HttpStatusCode.Created, JSON)

    private fun MockRequestHandleScope.refuse(code: ApiErrorCode, status: HttpStatusCode) =
        respond(ApiJson.encodeToString(ApiError.serializer(), ApiError(code)), status, JSON)

    private suspend fun current(): League? = leagues.league().first()

    @Test
    fun `refresh keeps the league, its dates and its table, with when it was fetched`() = runBlocking {
        serverLeague = summary()
        members = listOf(standing("Sora Example", points = 5), standing("Wren Example", you = true, points = 3))

        assertNull(current())
        assertEquals(ServerResult.Ok(Unit), leagues.refresh())

        val league = current()!!
        assertEquals("Test League", league.name)
        assertEquals(CODE, league.inviteCode)
        assertEquals(LeaguePhase.FORMING, league.phase)
        assertEquals(ZoneId.of("America/Denver"), league.timeZone)
        assertEquals(SEASON_START, league.calendar?.startDate)
        assertEquals(listOf("Sora Example", "Wren Example"), league.members.map { it.displayName })
        assertEquals(3, league.you?.points)
        assertEquals(clock.now, league.fetchedAt)
    }

    @Test
    fun `offline, the league last fetched stays, with its time`() = runBlocking {
        serverLeague = summary()
        leagues.refresh()
        val fetched = current()

        online = false
        clock.now = clock.now.plusSeconds(3600)
        assertEquals(ServerResult.Failed(ServerProblem.NoNetwork), leagues.refresh())
        assertEquals(fetched, current())
    }

    @Test
    fun `no league on the server leaves none on the phone`() = runBlocking {
        serverLeague = summary()
        leagues.refresh()
        serverLeague = null

        assertEquals(ServerResult.Ok(Unit), leagues.refresh())
        assertNull(current())
    }

    @Test
    fun `a closed league keeps why`() = runBlocking {
        serverLeague = summary(state = LeagueState.CLOSED, closedBecause = CloseReason.TOO_FEW_PLAYERS)
        leagues.refresh()

        assertEquals(LeaguePhase.CLOSED, current()?.phase)
        assertEquals(CloseReason.TOO_FEW_PLAYERS, current()?.closedBecause)
    }

    @Test
    fun `with no account nothing is sent and there is no league`() = runBlocking {
        serverLeague = summary()
        leagues.refresh()
        accountStore.account = null
        val noAccount = LeagueRepository(client(), AccountRepository(client(), accountStore, settings("league_test_2")), db.leagues(), clock)
        requests.clear()

        val unauthorized = ServerResult.Failed(ServerProblem.Refused(ApiErrorCode.UNAUTHORIZED))
        assertEquals(unauthorized, noAccount.refresh())
        assertEquals(unauthorized, noAccount.join(CODE))
        assertEquals(unauthorized, noAccount.findLeague())
        assertEquals(unauthorized, noAccount.create("Test League", open = true, ZoneId.of("America/Denver"), SEASON_START))
        assertEquals(unauthorized, noAccount.leave())
        assertNull(noAccount.league().first())
        assertTrue(requests.toString(), requests.isEmpty())
    }

    @Test
    fun `creating sends the name, open, the zone and the start, then keeps the league`() = runBlocking {
        val created = leagues.create("  Test League ", open = false, ZoneId.of("America/Denver"), SEASON_START)

        assertEquals(CODE, (created as ServerResult.Ok).value.inviteCode)
        val sent = requests.first { it.url.encodedPath == ApiPaths.LEAGUES }
        assertEquals(
            CreateLeagueRequest(name = "Test League", open = false, timeZone = "America/Denver", seasonStart = SEASON_START),
            ApiJson.decodeFromString(CreateLeagueRequest.serializer(), (sent.body as TextContent).text),
        )
        // Only the four fields of ADR 0001's table.
        assertEquals("""{"name":"Test League","open":false,"timeZone":"America/Denver","seasonStart":"2027-03-01"}""", (sent.body as TextContent).text)
        assertEquals("Test League", current()?.name)
        assertEquals(listOf(true), current()?.members?.map { it.you })
    }

    @Test
    fun `a name, a start or a zone the rules refuse is not sent`() = runBlocking {
        val denver = ZoneId.of("America/Denver")

        assertEquals(refused(ApiErrorCode.LEAGUE_NAME_INVALID), leagues.create("TL", open = true, denver, SEASON_START))
        assertEquals(refused(ApiErrorCode.LEAGUE_NAME_INVALID), leagues.create("   ", open = true, denver, SEASON_START))
        assertEquals(refused(ApiErrorCode.SEASON_START_NOT_A_MONDAY), leagues.create("Test League", open = true, denver, SEASON_START.plusDays(1)))
        // Monday 22 February is 5 days after Wednesday 17 February.
        assertEquals(refused(ApiErrorCode.SEASON_START_TOO_SOON), leagues.create("Test League", open = true, denver, LocalDate.of(2027, 2, 22)))
        assertEquals(refused(ApiErrorCode.TIME_ZONE_UNKNOWN), leagues.create("Test League", open = true, ZoneOffset.ofHours(2), SEASON_START))
        assertTrue(requests.toString(), requests.isEmpty())
    }

    @Test
    fun `a typed code is sent as the server reads it, and one that cannot be a code is not sent`() = runBlocking {
        assertEquals(refused(ApiErrorCode.INVITE_CODE_UNKNOWN), leagues.join("WREN-482"))
        assertTrue(requests.isEmpty())

        val joined = leagues.join(" ${CODE.lowercase()} ")

        assertEquals(LEAGUE, (joined as ServerResult.Ok).value.id)
        val sent = requests.first { it.url.encodedPath == ApiPaths.JOIN_LEAGUE }
        assertEquals(JoinLeagueRequest(CODE), ApiJson.decodeFromString(JoinLeagueRequest.serializer(), (sent.body as TextContent).text))
        assertEquals(2, current()?.members?.size)
    }

    @Test
    fun `a refusal to join comes back typed and keeps nothing`() = runBlocking {
        refuseWith = ApiErrorCode.LEAGUE_FULL to HttpStatusCode.Conflict

        assertEquals(refused(ApiErrorCode.LEAGUE_FULL), leagues.join(CODE))
        assertNull(current())
    }

    @Test
    fun `finding a league sends no body`() = runBlocking {
        assertEquals(LEAGUE, (leagues.findLeague() as ServerResult.Ok).value.id)

        val sent = requests.first { it.url.encodedPath == ApiPaths.AUTO_JOIN_LEAGUE }
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals(0L, sent.body.contentLength ?: 0L)
    }

    @Test
    fun `leaving deletes the league on the phone`() = runBlocking {
        serverLeague = summary()
        leagues.refresh()

        assertEquals(ServerResult.Ok(Unit), leagues.leave())
        assertEquals(HttpMethod.Delete, requests.first { it.url.encodedPath == ApiPaths.membership(LEAGUE) }.method)
        assertNull(current())
    }

    @Test
    fun `a league whose table cannot be read is kept from its summary`() = runBlocking {
        serverLeague = summary()
        refuseWith = null
        online = true
        // The join is answered; the league's detail is not.
        val joined = LeagueRepository(
            ServerClient(
                serverHttpClient(
                    MockEngine { request ->
                        if (request.url.encodedPath == ApiPaths.JOIN_LEAGUE) {
                            ok(LeagueSummary.serializer(), summary())
                        } else {
                            refuse(ApiErrorCode.SERVER_ERROR, HttpStatusCode.InternalServerError)
                        }
                    },
                ),
                "https://server.example",
            ),
            accounts,
            db.leagues(),
            clock,
        ).join(CODE)

        val league = (joined as ServerResult.Ok).value
        assertEquals(CODE, league.inviteCode)
        assertNull(league.calendar)
        assertTrue(league.members.isEmpty())
    }

    private fun refused(code: ApiErrorCode) = ServerResult.Failed(ServerProblem.Refused(code))

    private fun settings(name: String) = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(context.filesDir, "$name.preferences_pb").apply { delete() } },
    )

    private companion object {
        const val LEAGUE = "test-league-1"
        const val CODE = "TESTWREN"
        const val PLAYER_ID = "00000000-0000-4000-8000-000000000001"
        const val SECRET = "test-secret-not-real"
        val SEASON_START: LocalDate = LocalDate.of(2027, 3, 1)
        val JSON = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
