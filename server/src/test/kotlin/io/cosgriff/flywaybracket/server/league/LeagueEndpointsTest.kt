package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.BracketResponse
import io.cosgriff.flywaybracket.api.CreateAccountResponse
import io.cosgriff.flywaybracket.api.LeagueDetail
import io.cosgriff.flywaybracket.api.LeagueListResponse
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.LeagueSummary
import io.cosgriff.flywaybracket.api.MatchupSlot
import io.cosgriff.flywaybracket.api.PicksResponse
import io.cosgriff.flywaybracket.api.ProfileResponse
import io.cosgriff.flywaybracket.api.Standing
import io.cosgriff.flywaybracket.api.SubmitPicksRequest
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.server.account.Accounts
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.module
import io.cosgriff.flywaybracket.server.pack.TEST_HOME_COUNTY
import io.cosgriff.flywaybracket.server.pack.noPacks
import io.cosgriff.flywaybracket.server.pack.testCharts
import io.cosgriff.flywaybracket.server.sighting.InMemorySightingStore
import io.cosgriff.flywaybracket.server.sighting.Sightings
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.basicAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import org.junit.Assert.assertTrue
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
        val charts = testCharts()
        val leagues = Leagues(leagueStore(players), charts, clock)
        val packs = noPacks()
        application {
            module(
                database = { true },
                accounts = Accounts(players, leagues, charts),
                leagues = leagues,
                packs = packs,
                sightings = Sightings(InMemorySightingStore(players), packs),
                version = "1.2.3",
            )
        }
        test()
    }

    private fun HttpRequestBuilder.signedBy(account: CreateAccountResponse) = basicAuth(account.playerId, account.secret)

    /** A new account with a display name and a home county. */
    private suspend fun ApplicationTestBuilder.player(displayName: String = "Player One", homeCounty: String = COUNTY): CreateAccountResponse {
        val account = ApiJson.decodeFromString<CreateAccountResponse>(
            client.post(ApiPaths.ACCOUNTS) {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }.bodyAsText(),
        )
        val patched = client.patch(ApiPaths.ACCOUNT) {
            signedBy(account)
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"$displayName","homeCounty":"$homeCounty"}""")
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
        val keys = detail.members.map { it.memberKey }
        assertEquals(
            listOf(
                Standing("Player One", points = 0, rank = 1, memberKey = keys[0], you = true),
                Standing("Player Two", points = 0, rank = 1, memberKey = keys[1]),
            ),
            detail.members,
        )
        assertEquals(2, keys.toSet().size)
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
            client.get(ApiPaths.bracket("league-1")),
            client.get(ApiPaths.picks("league-1")),
            client.put(ApiPaths.picks("league-1")),
            client.get(ApiPaths.memberPicks("league-1", "member-1")),
            client.delete(ApiPaths.membership("league-1")),
        )

        unauthorized.forEach { it.assertRefused(HttpStatusCode.Unauthorized, ApiErrorCode.UNAUTHORIZED) }
    }

    // The bracket (#34)

    @Test
    fun `before the season the bracket is a preview of 64 birds, with no county in it`() = server {
        val owner = player()
        val league = createLeague(owner).body<LeagueSummary>()

        val response = client.get(ApiPaths.bracket(league.id)) { signedBy(owner) }

        assertEquals(HttpStatusCode.OK, response.status)
        val bracket = response.body<BracketResponse>()
        assertTrue(bracket.preview)
        assertEquals(64, bracket.teams.size)
        assertEquals(MatchupSlot.all, bracket.matchups.map { it.slot })
        assertEquals("Test Warbler 20", bracket.matchups.first().upper)
        val text = response.bodyAsText()
        assertFalse(text, text.contains(COUNTY))
    }

    @Test
    fun `once the season has started the bracket is the one it started with`() = server {
        val owner = player()
        val league = createLeague(owner).body<LeagueSummary>()
        val code = checkNotNull(league.inviteCode)
        repeat(7) { join(player("Player ${it + 2}"), code) }
        val preview = client.get(ApiPaths.bracket(league.id)) { signedBy(owner) }.body<BracketResponse>()
        // A minute after midnight on 1 March in Denver.
        clock.now = Instant.parse("2027-03-01T07:01:00Z")

        val started = client.get(ApiPaths.bracket(league.id)) { signedBy(owner) }.body<BracketResponse>()

        assertFalse(started.preview)
        assertEquals(preview.teams, started.teams)
        assertEquals(LeagueState.IN_SEASON, client.get(ApiPaths.league(league.id)) { signedBy(owner) }.body<LeagueDetail>().league.state)
    }

    @Test
    fun `a bracket the player cannot see is 404, and one with no field 409`() = server {
        val league = createLeague(player()).body<LeagueSummary>()
        val outsider = player("Player Two", homeCounty = "XX-QQ-001")

        client.get(ApiPaths.bracket(league.id)) { signedBy(outsider) }.assertRefused(HttpStatusCode.NotFound, ApiErrorCode.LEAGUE_NOT_FOUND)
        client.get(ApiPaths.bracket("league-1")) { signedBy(outsider) }.assertRefused(HttpStatusCode.NotFound, ApiErrorCode.LEAGUE_NOT_FOUND)

        // No pack holds the outsider's county, so a league of theirs alone has no birds to seed.
        val alone = createLeague(outsider).body<LeagueSummary>()
        client.get(ApiPaths.bracket(alone.id)) { signedBy(outsider) }.assertRefused(HttpStatusCode.Conflict, ApiErrorCode.BRACKET_NOT_READY)
        assertEquals(false, client.get(ApiPaths.ACCOUNT) { signedBy(outsider) }.body<ProfileResponse>().homeCountyInPacks)
    }

    // Picks (#35)

    private suspend fun ApplicationTestBuilder.putPicks(account: CreateAccountResponse, leagueId: String, picks: Map<MatchupSlot, String>): HttpResponse =
        client.put(ApiPaths.picks(leagueId)) {
            signedBy(account)
            contentType(ContentType.Application.Json)
            setBody(ApiJson.encodeToString(SubmitPicksRequest(picks)))
        }

    /** A whole bracket in which the bird drawn above wins every matchup. */
    private suspend fun ApplicationTestBuilder.upperWinsEverywhere(account: CreateAccountResponse, leagueId: String): Map<MatchupSlot, String> {
        val bracket = client.get(ApiPaths.bracket(leagueId)) { signedBy(account) }.body<BracketResponse>()
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

    /** A league of [owner] and 7 more, so that its season starts on 1 March. */
    private suspend fun ApplicationTestBuilder.leagueOfEight(owner: CreateAccountResponse): Pair<LeagueSummary, List<CreateAccountResponse>> {
        val league = createLeague(owner).body<LeagueSummary>()
        val others = List(7) { player("Player ${it + 2}").also { joiner -> join(joiner, checkNotNull(league.inviteCode)) } }
        return league to others
    }

    @Test
    fun `a whole bracket is 200 with the picks, and reads back`() = server {
        val owner = player()
        val league = createLeague(owner).body<LeagueSummary>()
        val picks = upperWinsEverywhere(owner, league.id)

        val response = putPicks(owner, league.id, picks)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(PicksResponse(picks, locked = false), response.body<PicksResponse>())
        assertEquals(PicksResponse(picks, locked = false), client.get(ApiPaths.picks(league.id)) { signedBy(owner) }.body<PicksResponse>())
    }

    @Test
    fun `a part of a bracket is 200, and replaces the whole`() = server {
        val owner = player()
        val league = createLeague(owner).body<LeagueSummary>()
        val whole = upperWinsEverywhere(owner, league.id)
        putPicks(owner, league.id, whole)
        val topQuarter = whole.filterKeys { it.index < 8 || it.index in 32..35 || it.index in 48..49 || it.index == 56 }

        val response = putPicks(owner, league.id, topQuarter)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(topQuarter, client.get(ApiPaths.picks(league.id)) { signedBy(owner) }.body<PicksResponse>().picks)
    }

    @Test
    fun `an inconsistent bracket is 422 naming its first bad slot`() = server {
        val owner = player()
        val league = createLeague(owner).body<LeagueSummary>()
        val whole = upperWinsEverywhere(owner, league.id)

        // The top semifinal's bird with no quarterfinal picked for it.
        val response = putPicks(owner, league.id, whole - MatchupSlot(56))

        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertEquals(ApiError(ApiErrorCode.PICKS_INCOMPLETE, slot = MatchupSlot(60)), response.body<ApiError>())
        // A bird that cannot play in the matchup.
        val elsewhere = putPicks(owner, league.id, mapOf(MatchupSlot(3) to whole.getValue(MatchupSlot(0))))
        assertEquals(HttpStatusCode.UnprocessableEntity, elsewhere.status)
        assertEquals(ApiError(ApiErrorCode.BIRD_UNKNOWN, slot = MatchupSlot(3)), elsewhere.body<ApiError>())
        // Nothing was stored.
        assertEquals(emptyMap<MatchupSlot, String>(), client.get(ApiPaths.picks(league.id)) { signedBy(owner) }.body<PicksResponse>().picks)
    }

    @Test
    fun `a slot outside the bracket is 400`() = server {
        val owner = player()
        val league = createLeague(owner).body<LeagueSummary>()

        val response = client.put(ApiPaths.picks(league.id)) {
            signedBy(owner)
            contentType(ContentType.Application.Json)
            setBody("""{"picks":{"63":"Test Warbler 20"}}""")
        }

        response.assertRefused(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
    }

    @Test
    fun `a submission one second after the lock is 409`() = server {
        val owner = player()
        val (league, _) = leagueOfEight(owner)
        val picks = upperWinsEverywhere(owner, league.id)
        // Midnight on 1 March in Denver is 07:00 UTC.
        clock.now = Instant.parse("2027-03-01T06:59:59Z")
        assertEquals(HttpStatusCode.OK, putPicks(owner, league.id, picks).status)

        clock.now = Instant.parse("2027-03-01T07:00:01Z")

        putPicks(owner, league.id, emptyMap()).assertRefused(HttpStatusCode.Conflict, ApiErrorCode.PICKS_LOCKED)
        assertEquals(PicksResponse(picks, locked = true), client.get(ApiPaths.picks(league.id)) { signedBy(owner) }.body<PicksResponse>())
    }

    @Test
    fun `another player's picks are 409 before the lock and 200 after`() = server {
        val owner = player()
        val (league, others) = leagueOfEight(owner)
        val other = others.first()
        val picks = upperWinsEverywhere(other, league.id)
        putPicks(other, league.id, picks)
        val members = client.get(ApiPaths.league(league.id)) { signedBy(owner) }.body<LeagueDetail>().members
        val otherKey = members.single { it.displayName == "Player 2" }.memberKey

        client.get(ApiPaths.memberPicks(league.id, otherKey)) { signedBy(owner) }
            .assertRefused(HttpStatusCode.Conflict, ApiErrorCode.PICKS_HIDDEN)

        clock.now = Instant.parse("2027-03-01T07:00:01Z")

        val response = client.get(ApiPaths.memberPicks(league.id, otherKey)) { signedBy(owner) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(PicksResponse(picks, locked = true), response.body<PicksResponse>())
        client.get(ApiPaths.memberPicks(league.id, other.playerId)) { signedBy(owner) }
            .assertRefused(HttpStatusCode.NotFound, ApiErrorCode.MEMBER_NOT_FOUND)
        client.get(ApiPaths.memberPicks(league.id, otherKey)) { signedBy(player("Player Nine")) }
            .assertRefused(HttpStatusCode.NotFound, ApiErrorCode.LEAGUE_NOT_FOUND)
    }

    @Test
    fun `picks of a league with no field are 409, and of one the player is not in 404`() = server {
        val league = createLeague(player()).body<LeagueSummary>()
        val outsider = player("Player Two", homeCounty = "XX-QQ-001")

        client.get(ApiPaths.picks(league.id)) { signedBy(outsider) }.assertRefused(HttpStatusCode.NotFound, ApiErrorCode.LEAGUE_NOT_FOUND)
        putPicks(outsider, league.id, emptyMap()).assertRefused(HttpStatusCode.NotFound, ApiErrorCode.LEAGUE_NOT_FOUND)

        val alone = createLeague(outsider).body<LeagueSummary>()
        putPicks(outsider, alone.id, emptyMap()).assertRefused(HttpStatusCode.Conflict, ApiErrorCode.BRACKET_NOT_READY)
    }

    private companion object {
        const val COUNTY = TEST_HOME_COUNTY
    }
}
