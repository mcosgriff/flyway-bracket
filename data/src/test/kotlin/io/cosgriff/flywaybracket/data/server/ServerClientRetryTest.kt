package io.cosgriff.flywaybracket.data.server

import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.CreateAccountResponse
import io.cosgriff.flywaybracket.api.CreateLeagueRequest
import io.cosgriff.flywaybracket.api.SubmitPicksRequest
import io.cosgriff.flywaybracket.api.UpdateProfileRequest
import io.cosgriff.flywaybracket.api.UploadSightingsRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertificateException
import java.time.LocalDate
import javax.net.ssl.SSLHandshakeException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one retry while the server wakes (#93), and the waking signal. The mock engine runs on the test's
 * dispatcher, so the waits are virtual: no test sleeps. The ids and secrets are made up; no test reaches a
 * server.
 */
class ServerClientRetryTest {
    private val credentials = Credentials(playerId = "00000000-0000-4000-8000-000000000001", secret = "made-up-secret")
    private val activity = ServerActivity()

    /** How many requests the server got. */
    private var requests = 0

    /** What the server answers each request with, by its number from 1. */
    private fun TestScope.client(answer: suspend MockRequestHandleScope.(number: Int, HttpRequestData) -> HttpResponseData): ServerClient {
        val config = MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler { request -> answer(++requests, request) }
        }
        return ServerClient(serverHttpClient(MockEngine(config)), BASE_URL, activity = activity)
    }

    /** Fails the first request as [first] does, and answers the rest with [then]. */
    private fun TestScope.failingOnce(
        first: suspend MockRequestHandleScope.() -> HttpResponseData,
        then: suspend MockRequestHandleScope.() -> HttpResponseData = { respond("", HttpStatusCode.NoContent) },
    ) = client { number, _ -> if (number == 1) first() else then() }

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private fun MockRequestHandleScope.apiError(status: HttpStatusCode, code: ApiErrorCode) =
        json(status, ApiJson.encodeToString(ApiError.serializer(), ApiError(code.name)))

    private val account = CreateAccountResponse(credentials.playerId, credentials.secret)

    private fun MockRequestHandleScope.created() =
        json(HttpStatusCode.Created, ApiJson.encodeToString(CreateAccountResponse.serializer(), account))

    // What the Worker and Cloudflare answer while the container cannot start: plain text, never an ApiError.
    private val notTheServer = listOf(
        HttpStatusCode.InternalServerError to "internal error; reference = 0123456789abcdef",
        HttpStatusCode.InternalServerError to "Failed to start container: the container did not start in time",
        HttpStatusCode.ServiceUnavailable to "There is no Container instance available at this time.",
        HttpStatusCode.BadGateway to "",
    )

    /** Every call that changes something or reads, one of each kind the client makes. */
    private val everyCall: List<Pair<String, suspend ServerClient.() -> ServerResult<*>>> = listOf(
        "create account" to { createAccount() },
        "create league" to {
            createLeague(credentials, CreateLeagueRequest("Test League", open = true, timeZone = "America/Denver", seasonStart = LocalDate.of(2027, 3, 1)))
        },
        "join league" to { joinLeague(credentials, "TESTWREN") },
        "auto-join" to { autoJoinLeague(credentials) },
        "update profile" to { updateProfile(credentials, UpdateProfileRequest(displayName = "Wren Example")) },
        "profile" to { profile(credentials) },
        "submit picks" to { submitPicks(credentials, "test-league-1", SubmitPicksRequest(emptyMap())) },
        "delete account" to { deleteAccount(credentials) },
        "upload sightings" to { uploadSightings(credentials, UploadSightingsRequest(emptyList())) },
    )

    @Test
    fun `a 5xx that is not an ApiError is tried once more, after the wait`() = runTest {
        notTheServer.forEach { (status, text) ->
            requests = 0
            val server = failingOnce(first = { respond(text, status) }, then = { created() })
            val before = testScheduler.currentTime

            assertEquals("$status $text", ServerResult.Ok(account), server.createAccount())
            assertEquals("$status $text", 2, requests)
            assertTrue("waited before the retry", testScheduler.currentTime - before >= 2_000)
        }
    }

    @Test
    fun `a connection that could not be made is tried once more, whatever the call`() = runTest {
        listOf(ConnectException("Connection refused"), SSLHandshakeException("Connection closed during handshake")).forEach { thrown ->
            everyCall.forEach { (name, send) ->
                requests = 0
                val server = failingOnce(first = { throw thrown })
                send(server)
                assertEquals("$name, $thrown", 2, requests)
            }
        }
    }

    @Test
    fun `a timeout is tried once more for a call that can be repeated`() = runTest {
        val repeatable = setOf("profile", "submit picks", "delete account", "upload sightings")
        everyCall.filter { it.first in repeatable }.forEach { (name, send) ->
            requests = 0
            val server = failingOnce(first = { throw SocketTimeoutException("timeout") })
            send(server)
            assertEquals(name, 2, requests)
        }
    }

    @Test
    fun `a timeout is not tried again for a call the server may have done`() = runTest {
        val notRepeatable = setOf("create account", "create league", "join league", "auto-join", "update profile")
        everyCall.filter { it.first in notRepeatable }.forEach { (name, send) ->
            requests = 0
            val server = failingOnce(first = { throw SocketTimeoutException("timeout") })
            assertEquals(name, ServerResult.Failed(ServerProblem.NoAnswer), send(server))
            assertEquals(name, 1, requests)
        }
    }

    @Test
    fun `a request that times out on the client is not tried again when it creates something`() = runTest {
        requests = 0
        val config = MockEngineConfig().apply {
            dispatcher = StandardTestDispatcher(testScheduler)
            addHandler {
                requests++
                delay(60_000)
                respond("", HttpStatusCode.NoContent)
            }
        }
        val server = ServerClient(serverHttpClient(MockEngine(config), ServerTimeouts(requestMillis = 5_000)), BASE_URL, activity = activity)

        assertEquals(ServerResult.Failed(ServerProblem.NoAnswer), server.createAccount())
        assertEquals(1, requests)
    }

    @Test
    fun `there is one retry, and the second failure is the answer`() = runTest {
        val server = client { _, _ -> respond("internal error; reference = 0123456789abcdef", HttpStatusCode.InternalServerError) }

        assertEquals(ServerResult.Failed(ServerProblem.Refused(code = null)), server.profile(credentials))
        assertEquals(2, requests)
    }

    @Test
    fun `a 4xx is never tried again`() = runTest {
        val refusals = listOf<suspend MockRequestHandleScope.() -> HttpResponseData>(
            { apiError(HttpStatusCode.Unauthorized, ApiErrorCode.UNAUTHORIZED) },
            { apiError(HttpStatusCode.TooManyRequests, ApiErrorCode.RATE_LIMITED) },
            { respond("<html>Not found</html>", HttpStatusCode.NotFound) },
        )
        refusals.forEach { refusal ->
            requests = 0
            val server = failingOnce(first = refusal)
            server.profile(credentials)
            assertEquals(1, requests)
        }
    }

    @Test
    fun `a 5xx from the server itself is never tried again`() = runTest {
        listOf(HttpStatusCode.InternalServerError, HttpStatusCode.ServiceUnavailable).forEach { status ->
            requests = 0
            val server = failingOnce(first = { apiError(status, ApiErrorCode.SERVER_ERROR) })
            assertEquals("$status", ServerResult.Failed(ServerProblem.Refused(ApiErrorCode.SERVER_ERROR)), server.profile(credentials))
            assertEquals("$status", 1, requests)
        }
    }

    @Test
    fun `no network on the phone, or a certificate refused, is never tried again`() = runTest {
        val certificateRefused = SSLHandshakeException("Trust anchor for certification path not found")
            .apply { initCause(CertificateException("not trusted")) }
        listOf(UnknownHostException("server.example"), certificateRefused).forEach { thrown ->
            requests = 0
            val server = failingOnce(first = { throw thrown })
            server.profile(credentials)
            assertEquals("$thrown", 1, requests)
        }
    }

    @Test
    fun `a pack download the Worker answered for is tried once more`() = runTest {
        val bytes = ByteArray(64) { it.toByte() }
        val server = failingOnce(
            first = { respond("There is no Container instance available at this time.", HttpStatusCode.ServiceUnavailable) },
            then = { respond(bytes, HttpStatusCode.OK, headersOf(HttpHeaders.ETag, "\"made-up-version\"")) },
        )

        val fetched = server.downloadPack(credentials, "XX-TS", heldVersion = null)

        assertEquals(2, requests)
        assertEquals(64, ((fetched as ServerResult.Ok).value as PackFetch.Received).bytes.size)
    }

    @Test
    fun `a pack download cut off on the way is not started again`() = runTest {
        val bytes = ByteArray(64) { it.toByte() }
        val server = client { _, _ ->
            val body = ByteChannel()
            body.writeFully(bytes, 0, bytes.size / 2)
            body.close(IOException("connection lost"))
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, bytes.size.toString()))
        }

        assertEquals(ServerResult.Failed(ServerProblem.NoAnswer), server.downloadPack(credentials, "XX-TS", heldVersion = null))
        assertEquals(1, requests)
    }

    @Test
    fun `a quick answer is not waking`() = runTest {
        var wakingSeen = false
        val server = client { _, _ ->
            wakingSeen = activity.waking.value
            created()
        }

        server.createAccount()

        assertFalse(wakingSeen)
        assertFalse(activity.waking.value)
    }

    @Test
    fun `a call waiting longer than two seconds is waking until it has its answer`() = runTest {
        val server = client { _, _ ->
            delay(8_000)
            created()
        }

        val call = async { server.createAccount() }
        advanceTimeBy(1_999)
        runCurrent()
        assertFalse("not yet", activity.waking.value)
        advanceTimeBy(2)
        runCurrent()
        assertTrue("past two seconds", activity.waking.value)

        assertEquals(ServerResult.Ok(account), call.await())
        assertFalse("answered", activity.waking.value)
    }

    @Test
    fun `a call on its retry is waking, and stops when it fails again`() = runTest {
        val seen = mutableListOf<Boolean>()
        val server = client { _, _ ->
            seen += activity.waking.value
            respond("internal error; reference = 0123456789abcdef", HttpStatusCode.InternalServerError)
        }

        server.profile(credentials)

        assertEquals(listOf(false, true), seen)
        assertFalse(activity.waking.value)
    }

    @Test
    fun `a download is waking only until the file starts to come`() = runTest {
        val bytes = ByteArray(64) { it.toByte() }
        val server = client { _, _ ->
            delay(5_000)
            respond(bytes, HttpStatusCode.OK)
        }
        val wakingAtProgress = mutableListOf<Boolean>()

        server.downloadPack(credentials, "XX-TS", heldVersion = null, onProgress = { _, _ -> wakingAtProgress += activity.waking.value })

        assertTrue(wakingAtProgress.isNotEmpty())
        assertEquals(false, wakingAtProgress.any { it })
    }

    private companion object {
        const val BASE_URL = "https://server.example"
    }
}
