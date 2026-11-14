package io.cosgriff.flywaybracket.server.sighting

import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.CreateAccountResponse
import io.cosgriff.flywaybracket.api.SightingUpload
import io.cosgriff.flywaybracket.api.UploadSightingsRequest
import io.cosgriff.flywaybracket.domain.pack.testStatePack
import io.cosgriff.flywaybracket.server.account.Accounts
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.league.HandClock
import io.cosgriff.flywaybracket.server.league.InMemoryLeagueStore
import io.cosgriff.flywaybracket.server.league.Leagues
import io.cosgriff.flywaybracket.server.module
import io.cosgriff.flywaybracket.server.pack.Packs
import io.cosgriff.flywaybracket.server.pack.writePack
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.basicAuth
import io.ktor.client.request.delete
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
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * `POST /v1/sightings` and `DELETE /v1/sightings/{clientId}` over HTTP, with the made-up Testland pack,
 * the same cases for each [SightingStore]: [InMemorySightingEndpointsTest] needs nothing,
 * [PostgresSightingEndpointsTest] a real Postgres. All birds, counties, ids and secrets are made up.
 */
abstract class SightingEndpointsTest {
    /** A store of players. */
    protected abstract fun players(): PlayerStore

    protected abstract fun sightingStore(players: PlayerStore): SightingStore

    @get:Rule
    val folder = TemporaryFolder()

    /**
     * 10:00 on Thursday 4 March 2027 in UTC, which is already midnight starting Friday 5 March at UTC+14.
     * The latest date a sighting may have is therefore Saturday 6 March.
     */
    private val clock = HandClock(Instant.parse("2027-03-04T10:00:00Z"))

    private lateinit var store: SightingStore

    private fun server(test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        writePack(folder.root.toPath(), testStatePack())
        val players = players()
        val leagues = Leagues(InMemoryLeagueStore(players))
        val packs = Packs(folder.root.toPath())
        store = sightingStore(players)
        application {
            module(
                database = { true },
                accounts = Accounts(players, leagues),
                leagues = leagues,
                packs = packs,
                sightings = Sightings(store, packs, clock),
                version = "1.2.3",
            )
        }
        test()
    }

    private suspend fun ApplicationTestBuilder.newAccount(): CreateAccountResponse = ApiJson.decodeFromString(
        client.post(ApiPaths.ACCOUNTS) {
            contentType(ContentType.Application.Json)
            setBody("{}")
        }.bodyAsText(),
    )

    private fun HttpRequestBuilder.signedBy(account: CreateAccountResponse) = basicAuth(account.playerId, account.secret)

    private suspend fun ApplicationTestBuilder.upload(account: CreateAccountResponse, body: String): HttpResponse =
        client.post(ApiPaths.SIGHTINGS) {
            signedBy(account)
            contentType(ContentType.Application.Json)
            setBody(body)
        }

    private suspend fun ApplicationTestBuilder.upload(account: CreateAccountResponse, vararg sightings: SightingUpload): HttpResponse =
        upload(account, ApiJson.encodeToString(UploadSightingsRequest(sightings.toList())))

    private suspend fun ApplicationTestBuilder.delete(account: CreateAccountResponse, clientId: String): HttpResponse =
        client.delete(ApiPaths.sighting(clientId)) { signedBy(account) }

    private fun storedOf(account: CreateAccountResponse): Set<StoredSighting> = store.of(UUID.fromString(account.playerId))

    private fun SightingUpload.stored() = StoredSighting(UUID.fromString(clientId), commonName, countyCode, date)

    private suspend fun HttpResponse.assertRefused(status: HttpStatusCode, code: ApiErrorCode) {
        assertEquals(bodyAsText(), status, this.status)
        assertEquals(ApiError(code), ApiJson.decodeFromString<ApiError>(bodyAsText()))
    }

    private suspend fun HttpResponse.assertNoContent() = assertEquals(bodyAsText(), HttpStatusCode.NoContent, status)

    @Test
    fun `uploaded sightings are stored with their bird, county, date and id`() = server {
        val player = newAccount()

        upload(player, warbler, sandpiper).assertNoContent()

        assertEquals(setOf(warbler.stored(), sandpiper.stored()), storedOf(player))
    }

    @Test
    fun `the same batch sent twice changes nothing`() = server {
        val player = newAccount()
        upload(player, warbler, sandpiper).assertNoContent()

        upload(player, warbler, sandpiper).assertNoContent()

        assertEquals(setOf(warbler.stored(), sandpiper.stored()), storedOf(player))
    }

    @Test
    fun `an id sent again with other fields keeps the first`() = server {
        val player = newAccount()
        upload(player, warbler).assertNoContent()

        upload(player, warbler.copy(commonName = "Test Sparrow", date = LocalDate.of(2027, 3, 1)), sandpiper).assertNoContent()

        assertEquals(setOf(warbler.stored(), sandpiper.stored()), storedOf(player))
    }

    @Test
    fun `two sightings with one id in a batch keep the first`() = server {
        val player = newAccount()

        upload(player, warbler, warbler.copy(commonName = "Test Sparrow")).assertNoContent()

        assertEquals(setOf(warbler.stored()), storedOf(player))
    }

    @Test
    fun `an id is a UUID in either case`() = server {
        val player = newAccount()
        val shouted = warbler.copy(clientId = warbler.clientId.uppercase())

        upload(player, shouted).assertNoContent()
        upload(player, warbler).assertNoContent()

        assertEquals(setOf(warbler.stored()), storedOf(player))
    }

    @Test
    fun `two players may use one id`() = server {
        val first = newAccount()
        val second = newAccount()

        upload(first, warbler).assertNoContent()
        upload(second, warbler.copy(commonName = "Test Sparrow")).assertNoContent()

        assertEquals(setOf(warbler.stored()), storedOf(first))
        assertEquals(setOf(warbler.copy(commonName = "Test Sparrow").stored()), storedOf(second))
    }

    @Test
    fun `an empty batch is 204`() = server {
        val player = newAccount()

        upload(player).assertNoContent()

        assertEquals(emptySet<StoredSighting>(), storedOf(player))
    }

    @Test
    fun `500 sightings in a request are taken, 501 are refused`() = server {
        val player = newAccount()
        val many = List(UploadSightingsRequest.MAX_SIGHTINGS + 1) { warbler.copy(clientId = UUID.randomUUID().toString()) }

        upload(player, *many.toTypedArray()).assertRefused(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        assertEquals(emptySet<StoredSighting>(), storedOf(player))

        upload(player, *many.dropLast(1).toTypedArray()).assertNoContent()
        assertEquals(500, storedOf(player).size)
    }

    @Test
    fun `a county in no pack is 422, and nothing in the batch is stored`() = server {
        val player = newAccount()

        upload(player, warbler, sandpiper.copy(countyCode = "XX-TS-999")).assertRefused(HttpStatusCode.UnprocessableEntity, ApiErrorCode.COUNTY_UNKNOWN)
        upload(player, warbler.copy(countyCode = "Testland")).assertRefused(HttpStatusCode.UnprocessableEntity, ApiErrorCode.COUNTY_UNKNOWN)

        assertEquals(emptySet<StoredSighting>(), storedOf(player))
    }

    @Test
    fun `a bird in no pack is 422, and nothing in the batch is stored`() = server {
        val player = newAccount()

        upload(player, warbler, sandpiper.copy(commonName = "Test Owl")).assertRefused(HttpStatusCode.UnprocessableEntity, ApiErrorCode.BIRD_UNKNOWN)

        assertEquals(emptySet<StoredSighting>(), storedOf(player))
    }

    @Test
    fun `a bird of another county's chart is taken`() = server {
        val player = newAccount()
        // West Square's chart lists the warbler and the sparrow; East Square's lists the sandpiper.
        val wanderer = sandpiper.copy(countyCode = "XX-TS-001")

        upload(player, wanderer).assertNoContent()

        assertEquals(setOf(wanderer.stored()), storedOf(player))
    }

    @Test
    fun `a date up to one day after today at UTC+14 is taken, one later is 422`() = server {
        val player = newAccount()
        val tomorrowAtTheDatelineEdge = warbler.copy(date = LocalDate.of(2027, 3, 6))

        upload(player, tomorrowAtTheDatelineEdge).assertNoContent()
        upload(player, sandpiper.copy(date = LocalDate.of(2027, 3, 7)))
            .assertRefused(HttpStatusCode.UnprocessableEntity, ApiErrorCode.SIGHTING_DATE_IN_FUTURE)

        assertEquals(setOf(tomorrowAtTheDatelineEdge.stored()), storedOf(player))
    }

    @Test
    fun `an old date is taken`() = server {
        val player = newAccount()
        val lastYear = warbler.copy(date = LocalDate.of(2026, 5, 12))

        upload(player, lastYear).assertNoContent()

        assertEquals(setOf(lastYear.stored()), storedOf(player))
    }

    /** ADR 0001: nothing beyond the bird, the county, the date and the id is accepted, not even to be dropped. */
    @Test
    fun `a sighting with any other field is 400, and nothing is stored`() = server {
        val player = newAccount()
        val extras = listOf(
            """"latitude":39.7,"longitude":-104.9""",
            """"time":"07:15"""",
            """"placeName":"Made-up Pond"""",
            """"count":3""",
        )

        extras.forEach { extra ->
            val body = """{"sightings":[{"clientId":"${warbler.clientId}","commonName":"Test Warbler","countyCode":"XX-TS-001","date":"2027-03-04",$extra}]}"""
            upload(player, body).assertRefused(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        }
        upload(player, """{"sightings":[],"homeLatitude":39.7}""").assertRefused(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)

        assertEquals(emptySet<StoredSighting>(), storedOf(player))
    }

    @Test
    fun `a sighting missing a field, or with a field of the wrong shape, is 400`() = server {
        val player = newAccount()

        listOf(
            """{"sightings":[{"clientId":"${warbler.clientId}","commonName":"Test Warbler","countyCode":"XX-TS-001"}]}""",
            """{"sightings":[{"clientId":"${warbler.clientId}","commonName":"Test Warbler","countyCode":"XX-TS-001","date":"4 March 2027"}]}""",
            """{"sightings":[{"clientId":"${warbler.clientId}","commonName":"Test Warbler","countyCode":"XX-TS-001","date":"2027-02-30"}]}""",
            """{"sightings":{"clientId":"${warbler.clientId}"}}""",
            """{}""",
        ).forEach { upload(player, it).assertRefused(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST) }

        assertEquals(emptySet<StoredSighting>(), storedOf(player))
    }

    @Test
    fun `an id that is not a UUID is 400`() = server {
        val player = newAccount()

        listOf("sighting-1", "", "6f1c0d4e00004000800000000000000a", "1-2-3-4-5").forEach { id ->
            upload(player, warbler, sandpiper.copy(clientId = id)).assertRefused(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        }

        assertEquals(emptySet<StoredSighting>(), storedOf(player))
    }

    @Test
    fun `a body that is not JSON is 415`() = server {
        val player = newAccount()

        client.post(ApiPaths.SIGHTINGS) {
            signedBy(player)
            contentType(ContentType.Text.Plain)
            setBody("Test Warbler, XX-TS-001, 2027-03-04")
        }.assertRefused(HttpStatusCode.UnsupportedMediaType, ApiErrorCode.INVALID_REQUEST)
    }

    @Test
    fun `no credentials is 401`() = server {
        client.post(ApiPaths.SIGHTINGS) {
            contentType(ContentType.Application.Json)
            setBody(ApiJson.encodeToString(UploadSightingsRequest(listOf(warbler))))
        }.assertRefused(HttpStatusCode.Unauthorized, ApiErrorCode.UNAUTHORIZED)
        client.delete(ApiPaths.sighting(warbler.clientId)).assertRefused(HttpStatusCode.Unauthorized, ApiErrorCode.UNAUTHORIZED)
    }

    @Test
    fun `deleting a sighting removes it and leaves the others`() = server {
        val player = newAccount()
        upload(player, warbler, sandpiper).assertNoContent()

        delete(player, warbler.clientId).assertNoContent()

        assertEquals(setOf(sandpiper.stored()), storedOf(player))
    }

    @Test
    fun `deleting a sighting twice is 404 the second time`() = server {
        val player = newAccount()
        upload(player, warbler).assertNoContent()
        delete(player, warbler.clientId).assertNoContent()

        delete(player, warbler.clientId).assertRefused(HttpStatusCode.NotFound, ApiErrorCode.SIGHTING_NOT_FOUND)
    }

    @Test
    fun `deleting a sighting that was never sent, or an id that is not one, is 404`() = server {
        val player = newAccount()

        delete(player, UUID.randomUUID().toString()).assertRefused(HttpStatusCode.NotFound, ApiErrorCode.SIGHTING_NOT_FOUND)
        delete(player, "sighting-1").assertRefused(HttpStatusCode.NotFound, ApiErrorCode.SIGHTING_NOT_FOUND)
    }

    @Test
    fun `deleting another player's sighting is 404 and leaves it`() = server {
        val owner = newAccount()
        val other = newAccount()
        upload(owner, warbler).assertNoContent()

        delete(other, warbler.clientId).assertRefused(HttpStatusCode.NotFound, ApiErrorCode.SIGHTING_NOT_FOUND)

        assertEquals(setOf(warbler.stored()), storedOf(owner))
    }

    @Test
    fun `a deleted sighting can be sent again`() = server {
        val player = newAccount()
        upload(player, warbler).assertNoContent()
        delete(player, warbler.clientId).assertNoContent()

        val changed = warbler.copy(commonName = "Test Sparrow")
        upload(player, changed).assertNoContent()

        assertEquals(setOf(changed.stored()), storedOf(player))
    }

    @Test
    fun `deleting the account deletes its sightings and leaves the others'`() = server {
        val deleted = newAccount()
        val kept = newAccount()
        upload(deleted, warbler, sandpiper).assertNoContent()
        upload(kept, sandpiper).assertNoContent()

        assertEquals(HttpStatusCode.NoContent, client.delete(ApiPaths.ACCOUNT) { signedBy(deleted) }.status)

        assertEquals(emptySet<StoredSighting>(), storedOf(deleted))
        assertEquals(setOf(sandpiper.stored()), storedOf(kept))
        upload(deleted, warbler).assertRefused(HttpStatusCode.Unauthorized, ApiErrorCode.UNAUTHORIZED)
    }

    private companion object {
        val warbler = SightingUpload("6f1c0d4e-0000-4000-8000-00000000000a", "Test Warbler", "XX-TS-001", LocalDate.of(2027, 3, 4))
        val sandpiper = SightingUpload("6f1c0d4e-0000-4000-8000-00000000000b", "Test Sandpiper", "XX-TS-002", LocalDate.of(2027, 3, 5))
    }
}
