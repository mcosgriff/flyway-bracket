package io.cosgriff.flywaybracket.data.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.BracketMatchup
import io.cosgriff.flywaybracket.api.BracketResponse
import io.cosgriff.flywaybracket.api.BracketTeam
import io.cosgriff.flywaybracket.api.LeagueDetail
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.LeagueSummary
import io.cosgriff.flywaybracket.api.MatchupSlot
import io.cosgriff.flywaybracket.api.PicksResponse
import io.cosgriff.flywaybracket.api.ProfileResponse
import io.cosgriff.flywaybracket.api.SightingUpload
import io.cosgriff.flywaybracket.api.UploadSightingsRequest
import io.cosgriff.flywaybracket.data.AccountRepository
import io.cosgriff.flywaybracket.data.BracketRepository
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.data.account.AccountStore
import io.cosgriff.flywaybracket.data.account.StoredAccount
import io.cosgriff.flywaybracket.data.db.FlywayDatabase
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.serverHttpClient
import io.cosgriff.flywaybracket.domain.bracket.BirdGroup
import io.cosgriff.flywaybracket.domain.bracket.Field
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.bracket.testTeams
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
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.serialization.KSerializer

/**
 * The phone's database, account and repositories, and a league server in memory (Ktor's mock engine), for
 * the sync tests (#43). The league, account, birds and dates are made up.
 */
class SyncFixture(name: String, withAccount: Boolean = true) : AutoCloseable {
    /** A clock the test sets by hand, in UTC. */
    class TestClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

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
    val db = Room.inMemoryDatabaseBuilder<FlywayDatabase>(context).build()

    /** In round 2 of the season. */
    val clock = TestClock(Instant.parse("2027-03-10T18:00:00Z"))

    // The server's side.

    /** False: the phone cannot reach the server. */
    var online = true

    /** The player's league, as the profile names it; null for none. */
    var league: LeagueState? = LeagueState.IN_SEASON

    /** The sightings the server holds, by id: the first sent with an id is kept. */
    val stored = LinkedHashMap<String, SightingUpload>()

    /** How many more uploads the server stores and then answers as if it never had (a lost answer). */
    var loseAnswers = 0

    /** Birds the server does not know: an upload with one is refused whole. */
    val unknownBirds = mutableSetOf<String>()

    val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())

    private val accountStore = MemoryAccountStore(StoredAccount(PLAYER_ID, SECRET).takeIf { withAccount })
    private val server = ServerClient(serverHttpClient(MockEngine { request -> answer(request) }), "https://server.example")
    val accounts = AccountRepository(
        server = server,
        store = accountStore,
        settings = SettingsRepository(
            PreferenceDataStoreFactory.create(scope = scope) { File(context.filesDir, "$name.preferences_pb").apply { delete() } },
        ),
    )
    val outings = OutingRepository(db.outings(), clock)
    val brackets = BracketRepository(server, accounts, db.brackets(), db.outings(), clock)
    val sync = SightingSync(server, accounts, brackets, db.sync(), clock)

    /** A finished outing in a made-up county, with [birds] logged at [at]; returns its id. */
    suspend fun outing(at: Instant, vararg birds: String): Long {
        val now = clock.now
        clock.now = at
        val id = outings.start("Test Pond", COUNTY).id
        birds.forEach { outings.addOrBump(id, it) }
        clock.now = at + Duration.ofMinutes(30)
        outings.end()
        clock.now = now
        return id
    }

    fun uploads(): List<UploadSightingsRequest> = requests
        .filter { it.method == HttpMethod.Post && it.url.encodedPath == ApiPaths.SIGHTINGS }
        .map { ApiJson.decodeFromString(UploadSightingsRequest.serializer(), (it.body as TextContent).text) }

    fun uploadBodies(): List<String> = requests
        .filter { it.method == HttpMethod.Post && it.url.encodedPath == ApiPaths.SIGHTINGS }
        .map { (it.body as TextContent).text }

    fun deletes(): List<String> = requests
        .filter { it.method == HttpMethod.Delete && it.url.encodedPath.startsWith(ApiPaths.SIGHTINGS + "/") }
        .map { it.url.encodedPath.substringAfterLast('/') }

    override fun close() {
        db.close()
        scope.cancel()
    }

    private fun MockRequestHandleScope.answer(request: HttpRequestData) = run {
        if (!online) throw UnknownHostException("server.example")
        requests += request
        val path = request.url.encodedPath
        when {
            path == ApiPaths.ACCOUNT && request.method == HttpMethod.Get -> ok(ProfileResponse.serializer(), profile())
            path == ApiPaths.league(LEAGUE) -> ok(LeagueDetail.serializer(), leagueDetail())
            path == ApiPaths.bracket(LEAGUE) -> ok(BracketResponse.serializer(), bracketResponse())
            path == ApiPaths.picks(LEAGUE) -> ok(PicksResponse.serializer(), PicksResponse(emptyMap(), locked = true))
            path.startsWith(ApiPaths.league(LEAGUE) + "/rounds/") -> refuse(ApiErrorCode.ROUND_NOT_DECIDED, HttpStatusCode.Conflict)
            path == ApiPaths.SIGHTINGS && request.method == HttpMethod.Post -> {
                val sent = ApiJson.decodeFromString(UploadSightingsRequest.serializer(), (request.body as TextContent).text)
                when {
                    sent.sightings.any { it.commonName in unknownBirds } ->
                        refuse(ApiErrorCode.BIRD_UNKNOWN, HttpStatusCode.UnprocessableEntity)
                    else -> {
                        sent.sightings.forEach { stored.putIfAbsent(it.clientId, it) }
                        if (loseAnswers > 0) {
                            loseAnswers--
                            // A proxy's answer with no body: the phone cannot tell the sightings arrived.
                            respond("", HttpStatusCode.BadGateway)
                        } else {
                            respond("", HttpStatusCode.NoContent)
                        }
                    }
                }
            }
            path.startsWith(ApiPaths.SIGHTINGS + "/") && request.method == HttpMethod.Delete -> {
                if (stored.remove(path.substringAfterLast('/')) != null) {
                    respond("", HttpStatusCode.NoContent)
                } else {
                    refuse(ApiErrorCode.SIGHTING_NOT_FOUND, HttpStatusCode.NotFound)
                }
            }
            else -> refuse(ApiErrorCode.INVALID_REQUEST, HttpStatusCode.NotFound)
        }
    }

    private fun <T> MockRequestHandleScope.ok(serializer: KSerializer<T>, value: T) =
        respond(ApiJson.encodeToString(serializer, value), HttpStatusCode.OK, JSON)

    private fun MockRequestHandleScope.refuse(code: ApiErrorCode, status: HttpStatusCode) =
        respond(ApiJson.encodeToString(ApiError.serializer(), ApiError(code)), status, JSON)

    private fun summary() = league?.let { LeagueSummary(id = LEAGUE, name = "Test League", players = 8, capacity = 64, state = it) }

    private fun profile() = ProfileResponse(displayName = "Test Player", homeCounty = COUNTY, league = summary())

    private fun leagueDetail() = LeagueDetail(
        league = summary() ?: LeagueSummary(id = LEAGUE, name = "Test League", players = 8, capacity = 64, state = LeagueState.IN_SEASON),
        season = Season.SPRING,
        timeZone = "UTC",
        seasonStart = SEASON_START,
        seasonEnd = SEASON_START.plusWeeks(6).minusDays(1),
        members = emptyList(),
    )

    private fun bracketResponse(): BracketResponse {
        val teams = testTeams()
        val order = Field.drawOrder(teams).map { it.commonName }
        return BracketResponse(
            teams = teams.map { BracketTeam(it.commonName, BirdGroup.valueOf(it.quarter), it.seed) },
            matchups = MatchupSlot.all.map { slot ->
                val first = slot.round == Round.ROUND_OF_64
                BracketMatchup(
                    slot = slot,
                    upper = if (first) order[2 * slot.position] else null,
                    lower = if (first) order[2 * slot.position + 1] else null,
                    winner = null,
                )
            },
            preview = false,
        )
    }

    companion object {
        const val LEAGUE = "test-league-1"
        const val PLAYER_ID = "00000000-0000-4000-8000-000000000001"
        const val SECRET = "test-secret-not-real"
        const val COUNTY = "US-ZZ-001"
        val SEASON_START: LocalDate = LocalDate.of(2027, 3, 1)

        /** Noon on a day of round 2. */
        val IN_SEASON: Instant = Instant.parse("2027-03-09T12:00:00Z")

        /** Noon on a day of round 1. */
        val ROUND_1: Instant = Instant.parse("2027-03-02T12:00:00Z")

        /** Noon a week before the season. */
        val BEFORE_SEASON: Instant = Instant.parse("2027-02-22T12:00:00Z")

        val JSON = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
