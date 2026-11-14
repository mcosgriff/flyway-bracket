package io.cosgriff.flywaybracket.server.pack

import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.CreateAccountResponse
import io.cosgriff.flywaybracket.api.PackListResponse
import io.cosgriff.flywaybracket.api.PackSummary
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import io.cosgriff.flywaybracket.domain.pack.testStatePack
import io.cosgriff.flywaybracket.server.account.Accounts
import io.cosgriff.flywaybracket.server.account.InMemoryPlayerStore
import io.cosgriff.flywaybracket.server.league.InMemoryLeagueStore
import io.cosgriff.flywaybracket.server.league.Leagues
import io.cosgriff.flywaybracket.server.module
import io.cosgriff.flywaybracket.server.sighting.InMemorySightingStore
import io.cosgriff.flywaybracket.server.sighting.Sightings
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.nio.file.Path

/**
 * `GET /v1/packs` and `GET /v1/packs/{state}` over HTTP, with the made-up Testland pack written to a
 * temporary directory. Needs no database. All names and secrets are made up.
 */
class PackEndpointsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val directory: Path get() = folder.root.toPath()

    private fun server(test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        val players = InMemoryPlayerStore()
        val leagues = Leagues(InMemoryLeagueStore(players))
        val packs = Packs(directory)
        application {
            module(
                database = { true },
                accounts = Accounts(players, leagues),
                leagues = leagues,
                packs = packs,
                sightings = Sightings(InMemorySightingStore(players), packs),
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

    private suspend fun HttpResponse.error(): ApiError = ApiJson.decodeFromString(bodyAsText())

    @Test
    fun `the list gives each pack`() {
        val bytes = writePack(directory, testStatePack())
        server {
            val response = client.get(ApiPaths.PACKS) { signedBy(newAccount()) }

            assertEquals(HttpStatusCode.OK, response.status)
            val expected = PackSummary("XX-TS", "Testland", bytes.size.toLong(), testStatePack().builtOn, Packs.version(bytes))
            assertEquals(PackListResponse(listOf(expected)), ApiJson.decodeFromString<PackListResponse>(response.bodyAsText()))
        }
    }

    @Test
    fun `the file comes with its version as the ETag, and decodes to the pack`() {
        val bytes = writePack(directory, testStatePack())
        server {
            val response = client.get(ApiPaths.pack("XX-TS")) { signedBy(newAccount()) }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("\"${Packs.version(bytes)}\"", response.headers[HttpHeaders.ETag])
            assertEquals(ContentType.Application.OctetStream, response.contentType())
            val body = response.bodyAsBytes()
            assertArrayEquals(bytes, body)
            assertEquals(StatePackResult.Read(testStatePack()), StatePackCodec.decode(body))
        }
    }

    @Test
    fun `If-None-Match with the version answers 304 with no body`() {
        val bytes = writePack(directory, testStatePack())
        val etag = "\"${Packs.version(bytes)}\""
        server {
            val account = newAccount()
            listOf(etag, "W/$etag", "\"made-up-older-version\", $etag", "*").forEach { ifNoneMatch ->
                val response = client.get(ApiPaths.pack("XX-TS")) {
                    signedBy(account)
                    header(HttpHeaders.IfNoneMatch, ifNoneMatch)
                }

                assertEquals(ifNoneMatch, HttpStatusCode.NotModified, response.status)
                assertEquals(ifNoneMatch, etag, response.headers[HttpHeaders.ETag])
                assertEquals(ifNoneMatch, 0, response.bodyAsBytes().size)
            }
        }
    }

    @Test
    fun `If-None-Match with an older version sends the file`() {
        val bytes = writePack(directory, testStatePack())
        server {
            val response = client.get(ApiPaths.pack("XX-TS")) {
                signedBy(newAccount())
                header(HttpHeaders.IfNoneMatch, "\"made-up-older-version\"")
            }

            assertEquals(HttpStatusCode.OK, response.status)
            assertArrayEquals(bytes, response.bodyAsBytes())
        }
    }

    @Test
    fun `an unknown state is 404 with PACK_NOT_FOUND`() {
        writePack(directory, testStatePack())
        server {
            val response = client.get(ApiPaths.pack("XX-ZZ")) { signedBy(newAccount()) }

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(ApiError(ApiErrorCode.PACK_NOT_FOUND), response.error())
        }
    }

    @Test
    fun `a state that is a path reaches no file`() {
        writePack(directory, testStatePack())
        write(directory, "secret.txt", "made-up secret".toByteArray())
        server {
            val account = newAccount()
            listOf("..%2Fsecret.txt", "XX-TS.fbpack", "xx-ts", "%2E%2E").forEach { state ->
                val response = client.get("${ApiPaths.PACKS}/$state") { signedBy(account) }

                assertEquals(state, HttpStatusCode.NotFound, response.status)
                assertEquals(state, ApiError(ApiErrorCode.PACK_NOT_FOUND), response.error())
            }
        }
    }

    @Test
    fun `a corrupt file in the directory does not stop the server, and the good packs are served`() {
        writePack(directory, testStatePack())
        write(directory, "XX-OT.fbpack", "not a pack".toByteArray())
        server {
            val account = newAccount()
            val list = ApiJson.decodeFromString<PackListResponse>(client.get(ApiPaths.PACKS) { signedBy(account) }.bodyAsText())

            assertEquals(listOf("XX-TS"), list.packs.map { it.stateCode })
            val corrupt = client.get(ApiPaths.pack("XX-OT")) { signedBy(account) }
            assertEquals(HttpStatusCode.NotFound, corrupt.status)
            assertEquals(ApiError(ApiErrorCode.PACK_NOT_FOUND), corrupt.error())
        }
    }

    @Test
    fun `both need a signed-in player`() {
        writePack(directory, testStatePack())
        server {
            listOf(ApiPaths.PACKS, ApiPaths.pack("XX-TS")).forEach { path ->
                val unsigned = client.get(path)
                assertEquals(path, HttpStatusCode.Unauthorized, unsigned.status)
                assertEquals(path, ApiError(ApiErrorCode.UNAUTHORIZED), unsigned.error())

                val wrong = client.get(path) { basicAuth("made-up-player", "made-up-wrong-secret") }
                assertEquals(path, HttpStatusCode.Unauthorized, wrong.status)
                assertTrue(path, wrong.headers[HttpHeaders.ETag] == null)
            }
        }
    }
}
