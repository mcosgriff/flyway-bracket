package io.cosgriff.flywaybracket.server.account

import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.CreateAccountResponse
import io.cosgriff.flywaybracket.api.ProfileResponse
import io.cosgriff.flywaybracket.server.league.LeagueStore
import io.cosgriff.flywaybracket.server.league.Leagues
import io.cosgriff.flywaybracket.server.pack.testCharts
import io.cosgriff.flywaybracket.server.module
import io.cosgriff.flywaybracket.server.pack.noPacks
import io.cosgriff.flywaybracket.server.sighting.InMemorySightingStore
import io.cosgriff.flywaybracket.server.sighting.Sightings
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.basicAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.UUID

/**
 * The account endpoints over HTTP, the same cases for each [PlayerStore]: [InMemoryAccountEndpointsTest]
 * needs nothing, [PostgresAccountEndpointsTest] a real Postgres. All names, codes and secrets are made up.
 */
abstract class AccountEndpointsTest {
    /** A store holding no player the test has made. */
    protected abstract fun store(): PlayerStore

    /** The leagues of [players]' players. */
    protected abstract fun leagueStore(players: PlayerStore): LeagueStore

    protected fun server(creationsPerAddress: Int = 100, test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val players = store()
        val packs = noPacks()
        val charts = testCharts()
        val leagues = Leagues(leagueStore(players), charts)
        val accounts = Accounts(players, leagues, charts, creationLimit = FixedWindowRateLimiter(creationsPerAddress, Accounts.CREATION_WINDOW))
        application {
            module(
                database = { true },
                accounts = accounts,
                leagues = leagues,
                packs = packs,
                sightings = Sightings(InMemorySightingStore(players), packs),
                version = "1.2.3",
            )
        }
        test()
    }

    protected suspend fun ApplicationTestBuilder.createAccount(body: String = "{}"): HttpResponse = client.post(ApiPaths.ACCOUNTS) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    protected suspend fun ApplicationTestBuilder.newAccount(): CreateAccountResponse {
        val response = createAccount()
        assertEquals(HttpStatusCode.Created, response.status)
        return ApiJson.decodeFromString(response.bodyAsText())
    }

    protected fun HttpRequestBuilder.signedBy(account: CreateAccountResponse) = basicAuth(account.playerId, account.secret)

    protected suspend fun ApplicationTestBuilder.profile(account: CreateAccountResponse): ProfileResponse {
        val response = client.get(ApiPaths.ACCOUNT) { signedBy(account) }
        assertEquals(HttpStatusCode.OK, response.status)
        return ApiJson.decodeFromString(response.bodyAsText())
    }

    protected suspend fun ApplicationTestBuilder.patch(account: CreateAccountResponse, body: String): HttpResponse =
        client.patch(ApiPaths.ACCOUNT) {
            signedBy(account)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    protected suspend fun HttpResponse.error(): ApiError = ApiJson.decodeFromString(bodyAsText())

    private suspend fun HttpResponse.assertRefused(status: HttpStatusCode, code: ApiErrorCode) {
        assertEquals(status, this.status)
        assertEquals(ApiError(code), error())
    }

    private suspend fun HttpResponse.assertUnauthorized() = assertRefused(HttpStatusCode.Unauthorized, ApiErrorCode.UNAUTHORIZED)

    @Test
    fun `creating an account gives an id and a secret, and holds nothing about the player`() = server {
        val account = newAccount()

        UUID.fromString(account.playerId)
        assertEquals(43, account.secret.length)
        assertEquals(ProfileResponse(), profile(account))
    }

    @Test
    fun `fields sent when creating an account are not kept`() = server {
        val response = createAccount("""{"displayName":"Player One","homeCounty":"US-ZZ-001"}""")
        val account = ApiJson.decodeFromString<CreateAccountResponse>(response.bodyAsText())

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(ProfileResponse(), profile(account))
    }

    @Test
    fun `two accounts are two players`() = server {
        val first = newAccount()
        val second = newAccount()

        assertNotEquals(first.playerId, second.playerId)
        assertNotEquals(first.secret, second.secret)
    }

    @Test
    fun `no credentials is 401`() = server {
        val response = client.get(ApiPaths.ACCOUNT)

        response.assertUnauthorized()
        assertEquals("Basic realm=\"flyway-bracket\", charset=\"UTF-8\"", response.headers[HttpHeaders.WWWAuthenticate])
        client.patch(ApiPaths.ACCOUNT) {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }.assertUnauthorized()
        client.delete(ApiPaths.ACCOUNT).assertUnauthorized()
    }

    @Test
    fun `a wrong secret is 401`() = server {
        val account = newAccount()
        val wrong = account.copy(secret = account.secret.dropLast(1) + if (account.secret.endsWith("A")) "B" else "A")

        client.get(ApiPaths.ACCOUNT) { signedBy(wrong) }.assertUnauthorized()
        patch(wrong, """{"displayName":"Player One"}""").assertUnauthorized()
        client.delete(ApiPaths.ACCOUNT) { signedBy(wrong) }.assertUnauthorized()
        assertEquals(ProfileResponse(), profile(account))
    }

    @Test
    fun `another player's secret is 401`() = server {
        val account = newAccount()
        val other = newAccount()

        client.get(ApiPaths.ACCOUNT) { signedBy(account.copy(secret = other.secret)) }.assertUnauthorized()
    }

    @Test
    fun `an unknown or malformed id is 401`() = server {
        val account = newAccount()

        client.get(ApiPaths.ACCOUNT) { signedBy(account.copy(playerId = UUID.randomUUID().toString())) }.assertUnauthorized()
        client.get(ApiPaths.ACCOUNT) { signedBy(account.copy(playerId = "player-1")) }.assertUnauthorized()
    }

    @Test
    fun `credentials that are not Basic are 401`() = server {
        val account = newAccount()

        client.get(ApiPaths.ACCOUNT) {
            header(HttpHeaders.Authorization, "Bearer ${account.playerId}.${account.secret}")
        }.assertUnauthorized()
        client.get(ApiPaths.ACCOUNT) { header(HttpHeaders.Authorization, "Basic not*base64") }.assertUnauthorized()
    }

    @Test
    fun `setting the display name and home county`() = server {
        val account = newAccount()

        val response = patch(account, """{"displayName":"  Player One ","homeCounty":"US-ZZ-001"}""")

        assertEquals(HttpStatusCode.NoContent, response.status)
        assertEquals(ProfileResponse(displayName = "Player One", homeCounty = "US-ZZ-001", homeCountyInPacks = true), profile(account))
    }

    @Test
    fun `a field left out is not changed`() = server {
        val account = newAccount()
        patch(account, """{"displayName":"Player One","homeCounty":"US-ZZ-001"}""")

        assertEquals(HttpStatusCode.NoContent, patch(account, """{"homeCounty":"US-ZZ-003"}""").status)
        assertEquals(HttpStatusCode.NoContent, patch(account, "{}").status)

        assertEquals(ProfileResponse(displayName = "Player One", homeCounty = "US-ZZ-003", homeCountyInPacks = false), profile(account))
    }

    @Test
    fun `one player's change leaves the others`() = server {
        val account = newAccount()
        val other = newAccount()

        patch(account, """{"displayName":"Player One"}""")

        assertEquals(ProfileResponse(), profile(other))
    }

    @Test
    fun `a display name too short or too long is 422 and changes nothing`() = server {
        val account = newAccount()
        patch(account, """{"displayName":"Player One","homeCounty":"US-ZZ-001"}""")

        listOf(" P ", "   ", "P".repeat(25)).forEach { name ->
            patch(account, """{"displayName":"$name","homeCounty":"US-ZZ-003"}""")
                .assertRefused(HttpStatusCode.UnprocessableEntity, ApiErrorCode.DISPLAY_NAME_INVALID)
        }

        assertEquals(ProfileResponse(displayName = "Player One", homeCounty = "US-ZZ-001", homeCountyInPacks = true), profile(account))
    }

    @Test
    fun `a home county that is not a county code is 422 and changes nothing`() = server {
        val account = newAccount()

        patch(account, """{"displayName":"Player One","homeCounty":"US-ZZ"}""")
            .assertRefused(HttpStatusCode.UnprocessableEntity, ApiErrorCode.COUNTY_UNKNOWN)

        assertEquals(ProfileResponse(), profile(account))
    }

    @Test
    fun `a home county in no pack is accepted`() = server {
        val account = newAccount()

        assertEquals(HttpStatusCode.NoContent, patch(account, """{"homeCounty":"US-ZZ-999"}""").status)
    }

    @Test
    fun `a profile change that is not JSON of its shape is 400`() = server {
        val account = newAccount()

        patch(account, """{"displayName": {"first": "Player"}}""").assertRefused(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
    }

    @Test
    fun `deleting the account`() = server {
        val account = newAccount()
        patch(account, """{"displayName":"Player One","homeCounty":"US-ZZ-001"}""")

        assertEquals(HttpStatusCode.NoContent, client.delete(ApiPaths.ACCOUNT) { signedBy(account) }.status)
    }

    @Test
    fun `a deleted account is 401 everywhere`() = server {
        val account = newAccount()
        client.delete(ApiPaths.ACCOUNT) { signedBy(account) }

        client.get(ApiPaths.ACCOUNT) { signedBy(account) }.assertUnauthorized()
        patch(account, """{"displayName":"Player One"}""").assertUnauthorized()
        client.delete(ApiPaths.ACCOUNT) { signedBy(account) }.assertUnauthorized()
    }

    @Test
    fun `deleting one account leaves the others`() = server {
        val account = newAccount()
        val other = newAccount()
        patch(other, """{"displayName":"Player Two"}""")

        client.delete(ApiPaths.ACCOUNT) { signedBy(account) }

        assertEquals(ProfileResponse(displayName = "Player Two"), profile(other))
    }

    @Test
    fun `creating accounts past the rate limit is 429`() = server(creationsPerAddress = 2) {
        newAccount()
        newAccount()

        createAccount().assertRefused(HttpStatusCode.TooManyRequests, ApiErrorCode.RATE_LIMITED)
    }

    @Test
    fun `the rate limit does not stop the accounts already made`() = server(creationsPerAddress = 1) {
        val account = newAccount()
        createAccount()

        assertEquals(ProfileResponse(), profile(account))
    }
}
