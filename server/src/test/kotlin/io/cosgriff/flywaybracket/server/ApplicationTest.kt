package io.cosgriff.flywaybracket.server

import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.HealthResponse
import io.cosgriff.flywaybracket.api.UpdateProfileRequest
import io.cosgriff.flywaybracket.server.account.Accounts
import io.cosgriff.flywaybracket.server.account.InMemoryPlayerStore
import io.cosgriff.flywaybracket.server.league.InMemoryLeagueStore
import io.cosgriff.flywaybracket.server.league.Leagues
import io.cosgriff.flywaybracket.server.pack.noPacks
import io.cosgriff.flywaybracket.server.sighting.InMemorySightingStore
import io.cosgriff.flywaybracket.server.sighting.Sightings
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The server with no database behind it. All names and values are made up. */
class ApplicationTest {
    /** The server, plus routes only these tests have: one reads a body, one fails, one shows its log line. */
    private fun server(databaseAnswers: Boolean = true, test: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            val players = InMemoryPlayerStore()
            val packs = noPacks()
            val leagues = Leagues(InMemoryLeagueStore(players), packs)
            module(
                database = { databaseAnswers },
                accounts = Accounts(players, leagues, packs),
                leagues = leagues,
                packs = packs,
                sightings = Sightings(InMemorySightingStore(players), packs),
                version = "1.2.3",
            )
            routing {
                post(READS_A_BODY) {
                    call.receive<UpdateProfileRequest>()
                    call.respond(HttpStatusCode.NoContent)
                }
                get(FAILS) { error(FAILURE_TEXT) }
                post(ECHOES_ITS_LOG_LINE) {
                    call.response.status(HttpStatusCode.Accepted)
                    call.respondText(requestLogLine(call))
                }
            }
        }
        test()
    }

    private suspend fun HttpResponse.error(): ApiError = ApiJson.decodeFromString(bodyAsText())

    @Test
    fun `health gives the version and says the database answers`() = server {
        val response = client.get(ApiPaths.HEALTH)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(HealthResponse(version = "1.2.3", database = true), ApiJson.decodeFromString<HealthResponse>(response.bodyAsText()))
    }

    @Test
    fun `health still answers 200 when the database does not`() = server(databaseAnswers = false) {
        val response = client.get(ApiPaths.HEALTH)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(HealthResponse(version = "1.2.3", database = false), ApiJson.decodeFromString<HealthResponse>(response.bodyAsText()))
    }

    @Test
    fun `an unknown route is an ApiError`() = server {
        val response = client.get("/v1/no-such-thing")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals(ApiError(ApiErrorCode.INVALID_REQUEST), response.error())
    }

    @Test
    fun `a known route with the wrong method is an ApiError`() = server {
        val response = client.post(ApiPaths.HEALTH)

        assertEquals(HttpStatusCode.MethodNotAllowed, response.status)
        assertEquals(ApiError(ApiErrorCode.INVALID_REQUEST), response.error())
    }

    @Test
    fun `bad JSON is an ApiError that does not repeat the body`() = server {
        val response = client.post(READS_A_BODY) {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName": $BODY_MARKER""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ApiErrorCode.INVALID_REQUEST, response.error().errorCode)
        assertNull(response.error().detail)
    }

    @Test
    fun `JSON of the wrong shape is an ApiError`() = server {
        val response = client.post(READS_A_BODY) {
            contentType(ContentType.Application.Json)
            setBody("""{"displayName": {"first": "Player"}}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(ApiError(ApiErrorCode.INVALID_REQUEST), response.error())
    }

    @Test
    fun `a body that is not JSON is an ApiError`() = server {
        val response = client.post(READS_A_BODY) {
            contentType(ContentType.Text.Plain)
            setBody("Player One")
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
        assertEquals(ApiError(ApiErrorCode.INVALID_REQUEST), response.error())
    }

    @Test
    fun `a failure in the server is an ApiError that says nothing of the cause`() = server {
        val response = client.get(FAILS)

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        assertEquals(ApiError(ApiErrorCode.SERVER_ERROR), response.error())
    }

    @Test
    fun `a request's log line has its status, method and path, not its secret, query or body`() = server {
        // The test route answers with the line the server writes to its log for that same request.
        val response = client.post("$ECHOES_ITS_LOG_LINE?note=$QUERY_MARKER") {
            basicAuth(username = "player-1", password = SECRET_MARKER)
            contentType(ContentType.Application.Json)
            setBody("""{"displayName":"$BODY_MARKER"}""")
        }

        assertEquals("202 POST $ECHOES_ITS_LOG_LINE", response.bodyAsText())
    }

    private companion object {
        const val READS_A_BODY = "/test/reads-a-body"
        const val FAILS = "/test/fails"
        const val FAILURE_TEXT = "made-up failure"
        const val SECRET_MARKER = "made-up-secret-4f1c"
        const val QUERY_MARKER = "made-up-query-77aa"
        const val BODY_MARKER = "made-up-body-9e2b"
        const val ECHOES_ITS_LOG_LINE = "/test/echoes-its-log-line"
    }
}
