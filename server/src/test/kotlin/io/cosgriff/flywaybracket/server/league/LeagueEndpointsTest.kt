package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.CreateAccountResponse
import io.cosgriff.flywaybracket.api.LeagueDetail
import io.cosgriff.flywaybracket.api.LeagueListResponse
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.LeagueSummary
import io.cosgriff.flywaybracket.api.ProfileResponse
import io.cosgriff.flywaybracket.api.Standing
import io.cosgriff.flywaybracket.server.account.Accounts
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.module
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.basicAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.Instant

/**
 * The league endpoints over HTTP, the same cases for each pair of stores: [InMemoryLeagueEndpointsTest]
 * needs nothing, [PostgresLeagueEndpointsTest] a real Postgres. The rules themselves are tested in
 * [LeaguesTest]. All names, codes and counties are made up.
 */
abstract class LeagueEndpointsTest {
    /** A store holding no player and no league. */
    protected abstract fun players(): PlayerStore

    protected abstract fun leagueStore(players: PlayerStore): LeagueStore

    /** Wednesday 17 February 2027, noon in Denver. */
    private val clock = HandClock(Instant.parse("2027-02-17T19:00:00Z"))

    private fun server(test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val players = players()
        val leagues = Leagues(leagueStore(players), clock)
        application { module(database = { true }, accounts = Accounts(players, leagues), leagues = leagues, version = "1.2.3") }
        test()
    }

    private fun HttpRequestBuilder.signedBy(account: CreateAccountResponse) = basicAuth(account.playerId, account.secret)

    /** A new account with a display name and a home county. */
    private suspend fun ApplicationTestBuilder.player(displayName: String = "Player One"): CreateAccountResponse {
        val account = ApiJson.decodeFromString<CreateAccountResponse>(
            client.post(ApiPaths.ACCOUNTS) {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }.bodyAsText(),
        )
        val patched = client.patch(ApiPaths.ACCOUNT) {
            signedBy(account)
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"$displayName","homeCounty":"$COUNTY"}""")
        }
        assertEquals(HttpStatusCode.NoContent, patched.status)
        return account
    }

    private suspend fun ApplicationTestBuilder.post(path: String, account: CreateAccountResponse, body: String? = null): HttpResponse =
        client.post(path) {
            signedBy(account)
            if (body != null) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }

    private suspend fun ApplicationTestBuilder.createLeague(
        account: CreateAccountResponse,
        name: String = "Thursday Walkers",
        timeZone: String = "America/Denver",
        seasonStart: String = "2027-03-01",
    ): HttpResponse = post(ApiPaths.LEAGUES, account, """{"name":"$name","open":false,"timeZone":"$timeZone","seasonStart":"$seasonStart"}""")

    private suspend fun ApplicationTestBuilder.join(account: CreateAccountResponse, code: String): HttpResponse =
        post(ApiPaths.JOIN_LEAGUE, account, """{"inviteCode":"$code"}""")

    private suspend inline fun <reified T> HttpResponse.body(): T = ApiJson.decodeFromString(bodyAsText())

    private suspend fun HttpResponse.assertRefused(status: HttpStatusCode, code: ApiErrorCode) {
        assertEquals(bodyAsText(), status, this.status)
        assertEquals(ApiError(code), body<ApiError>())
    }

    @Test
    fun `creating a league is 201 with the league`() = server {
        val owner = player()

        val response = createLeague(owner)

        assertEquals(HttpStatusCode.Created, response.status)
        val league = response.body<LeagueSummary>()
        assertEquals("Thursday Walkers", league.name)
        assertEquals(1, league.players)
        assertEquals(64, league.capacity)
        assertEquals(LeagueState.BEFORE_SEASON, league.state)
        assertEquals(8, league.inviteCode?.length)
    }

    @Test
    fun `a league that cannot be created is 422`() = server {
        val owner = player()

        createLeague(owner, name = "Ow").assertRefused(HttpStatusCode.UnprocessableEntity, ApiErrorCode.LEAGUE_NAME_INVALID)
        createLeague(owner, timeZone = "Mars/Olympus_Mons").assertRefused(HttpStatusCode.UnprocessableEntity, ApiErrorCode.TIME_ZONE_UNKNOWN)
        createLeague(owner, seasonStart = "2027-03-02").assertRefused(HttpStatusCode.UnprocessableEntity, ApiErrorCode.SEASON_START_NOT_A_MONDAY)
        createLeague(owner, seasonStart = "2027-02-22").assertRefused(HttpStatusCode.UnprocessableEntity, ApiErrorCode.SEASON_START_TOO_SOON)
        assertEquals(LeagueListResponse(emptyList()), client.get(ApiPaths.LEAGUES) { signedBy(owner) }.body<LeagueListResponse>())
    }

    @Test
    fun `a date that is not a date is 400`() = server {
        createLeague(player(), seasonStart = "1 March").assertRefused(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
    }

    @Test
    fun `a player with no display name is 422`() = server {
        val account = ApiJson.decodeFromString<CreateAccountResponse>(
            client.post(ApiPaths.ACCOUNTS) {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }.bodyAsText(),
        )

        createLeague(account).assertRefused(HttpStatusCode.UnprocessableEntity, ApiErrorCode.DISPLAY_NAME_INVALID)
    }

    @Test
    fun `joining by invite code is 200 with the league`() = server {
        val league = createLeague(player()).body<LeagueSummary>()
        val joiner = player("Player Two")

        val response = join(joiner, checkNotNull(league.inviteCode).lowercase())

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(league.copy(players = 2), response.body<LeagueSummary>())
        assertEquals(LeagueListResponse(listOf(league.copy(players = 2))), client.get(ApiPaths.LEAGUES) { signedBy(joiner) }.body<LeagueListResponse>())
    }

    @Test
    fun `join refusals`() = server {
        val owner = player()
        val league = createLeague(owner).body<LeagueSummary>()
        val code = checkNotNull(league.inviteCode)
        val other = player("Player Two")
        createLeague(other)

        join(player("Player Three"), "ABCDEFGH").assertRefused(HttpStatusCode.NotFound, ApiErrorCode.INVITE_CODE_UNKNOWN)
        join(owner, code).assertRefused(HttpStatusCode.Conflict, ApiErrorCode.ALREADY_A_MEMBER)
        join(other, code).assertRefused(HttpStatusCode.Conflict, ApiErrorCode.ALREADY_IN_A_LEAGUE)
        post(ApiPaths.AUTO_JOIN_LEAGUE, other).assertRefused(HttpStatusCode.Conflict, ApiErrorCode.ALREADY_IN_A_LEAGUE)
    }

    @Test
    fun `joining a closed league is 409, and its player is told through the profile`() = server {
        val owner = player()
        val league = createLeague(owner).body<LeagueSummary>()
        // One player all along: the start moved back four times, then the league closed on 29 March.
        clock.now = Instant.parse("2027-03-29T07:00:00Z")

        join(player("Player Two"), checkNotNull(league.inviteCode)).assertRefused(HttpStatusCode.Conflict, ApiErrorCode.JOINING_CLOSED)
        assertEquals(LeagueState.CLOSED, client.get(ApiPaths.ACCOUNT) { signedBy(owner) }.body<ProfileResponse>().league?.state)
    }

    @Test
    fun `auto-join is 200 with the league it chose or opened`() = server {
        val first = post(ApiPaths.AUTO_JOIN_LEAGUE, player())
        val second = post(ApiPaths.AUTO_JOIN_LEAGUE, player("Player Two"))

        assertEquals(HttpStatusCode.OK, first.status)
        assertEquals(HttpStatusCode.OK, second.status)
        assertEquals(first.body<LeagueSummary>().copy(players = 2), second.body<LeagueSummary>())
    }

    @Test
    fun `a member sees the league's members by display name, points and rank, never a county or an id`() = server {
        val owner = player("Player One")
        val joiner = player("Player Two")
        val league = createLeague(owner).body<LeagueSummary>()
        join(joiner, checkNotNull(league.inviteCode))

        val response = client.get(ApiPaths.league(league.id)) { signedBy(owner) }

        assertEquals(HttpStatusCode.OK, response.status)
        val detail = response.body<LeagueDetail>()
        assertEquals(
            listOf(Standing("Player One", points = 0, rank = 1, you = true), Standing("Player Two", points = 0, rank = 1)),
            detail.members,
        )
        assertEquals("America/Denver", detail.timeZone)
        val text = response.bodyAsText()
        assertFalse(text, text.contains(COUNTY))
        assertFalse(text, text.contains(owner.playerId))
        assertFalse(text, text.contains(joiner.playerId))
    }

    @Test
    fun `a league the player is not in is 404`() = server {
        val league = createLeague(player()).body<LeagueSummary>()
        val stranger = player("Player Two")

        client.get(ApiPaths.league(league.id)) { signedBy(stranger) }.assertRefused(HttpStatusCode.NotFound, ApiErrorCode.LEAGUE_NOT_FOUND)
        client.get(ApiPaths.league("league-1")) { signedBy(stranger) }.assertRefused(HttpStatusCode.NotFound, ApiErrorCode.LEAGUE_NOT_FOUND)
        client.delete(ApiPaths.membership(league.id)) { signedBy(stranger) }.assertRefused(HttpStatusCode.NotFound, ApiErrorCode.LEAGUE_NOT_FOUND)
    }

    @Test
    fun `leaving is 204, and the league is gone from the player's list`() = server {
        val owner = player()
        val league = createLeague(owner).body<LeagueSummary>()

        assertEquals(HttpStatusCode.NoContent, client.delete(ApiPaths.membership(league.id)) { signedBy(owner) }.status)

        assertEquals(LeagueListResponse(emptyList()), client.get(ApiPaths.LEAGUES) { signedBy(owner) }.body<LeagueListResponse>())
        client.delete(ApiPaths.membership(league.id)) { signedBy(owner) }.assertRefused(HttpStatusCode.NotFound, ApiErrorCode.LEAGUE_NOT_FOUND)
    }

    @Test
    fun `the profile carries the player's league`() = server {
        val owner = player()
        val league = createLeague(owner).body<LeagueSummary>()

        val profile = client.get(ApiPaths.ACCOUNT) { signedBy(owner) }.body<ProfileResponse>()

        assertEquals(league, profile.league)
    }

    @Test
    fun `deleting the account leaves the league`() = server {
        val owner = player()
        val league = createLeague(owner).body<LeagueSummary>()
        val deleted = player("Player Two")
        join(deleted, checkNotNull(league.inviteCode))

        assertEquals(HttpStatusCode.NoContent, client.delete(ApiPaths.ACCOUNT) { signedBy(deleted) }.status)

        val detail = client.get(ApiPaths.league(league.id)) { signedBy(owner) }.body<LeagueDetail>()
        assertEquals(1, detail.league.players)
        assertEquals(listOf("Player One"), detail.members.map { it.displayName })
    }

    @Test
    fun `every league endpoint needs an account`() = server {
        val unauthorized = listOf(
            client.post(ApiPaths.LEAGUES),
            client.get(ApiPaths.LEAGUES),
            client.post(ApiPaths.JOIN_LEAGUE),
            client.post(ApiPaths.AUTO_JOIN_LEAGUE),
            client.get(ApiPaths.league("league-1")),
            client.delete(ApiPaths.membership("league-1")),
        )

        unauthorized.forEach { it.assertRefused(HttpStatusCode.Unauthorized, ApiErrorCode.UNAUTHORIZED) }
    }

    private companion object {
        const val COUNTY = "US-ZZ-001"
    }
}
