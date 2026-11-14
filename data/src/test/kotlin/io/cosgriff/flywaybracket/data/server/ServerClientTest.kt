package io.cosgriff.flywaybracket.data.server

import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.CreateAccountResponse
import io.cosgriff.flywaybracket.api.ProfileResponse
import io.cosgriff.flywaybracket.api.UpdateProfileRequest
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.net.ConnectException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// The ids, secrets, names and county codes are made up. No test reaches a server: Ktor's mock engine answers.
class ServerClientTest {
    private val requests = mutableListOf<HttpRequestData>()
    private val credentials = Credentials(playerId = "00000000-0000-4000-8000-000000000001", secret = "made-up-secret")

    private fun client(
        timeouts: ServerTimeouts = ServerTimeouts(),
        answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): ServerClient {
        val engine = MockEngine { request ->
            requests += request
            answer(request)
        }
        return ServerClient(serverHttpClient(engine, timeouts), BASE_URL)
    }

    private fun MockRequestHandleScope.json(status: HttpStatusCode, body: String) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private fun MockRequestHandleScope.refusal(status: HttpStatusCode, code: String) =
        json(status, ApiJson.encodeToString(ApiError.serializer(), ApiError(code, detail = "for logs only")))

    private val HttpRequestData.text: String? get() = (body as? TextContent)?.text

    @Test
    fun `creating an account sends an empty body and no credentials`() = runBlocking {
        val issued = CreateAccountResponse(credentials.playerId, credentials.secret)
        val server = client { json(HttpStatusCode.Created, ApiJson.encodeToString(CreateAccountResponse.serializer(), issued)) }

        assertEquals(ServerResult.Ok(issued), server.createAccount())
        val sent = requests.single()
        assertEquals(HttpMethod.Post, sent.method)
        assertEquals("$BASE_URL/v1/accounts", sent.url.toString())
        assertEquals("{}", sent.text)
        assertNull(sent.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `the profile is read with Basic credentials`() = runBlocking {
        val profile = ProfileResponse(displayName = "Wren Example", homeCounty = "US-ZZ-001")
        val server = client { json(HttpStatusCode.OK, ApiJson.encodeToString(ProfileResponse.serializer(), profile)) }

        assertEquals(ServerResult.Ok(profile), server.profile(credentials))
        val sent = requests.single()
        assertEquals(HttpMethod.Get, sent.method)
        assertEquals("$BASE_URL/v1/account", sent.url.toString())
        val expected = Base64.getEncoder().encodeToString("${credentials.playerId}:${credentials.secret}".toByteArray())
        assertEquals("Basic $expected", sent.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `an update sends only the fields it holds`() = runBlocking {
        val server = client { respond("", HttpStatusCode.NoContent) }

        assertEquals(ServerResult.Ok(Unit), server.updateProfile(credentials, UpdateProfileRequest(displayName = "Wren Example")))
        assertEquals(ServerResult.Ok(Unit), server.updateProfile(credentials, UpdateProfileRequest(homeCounty = "US-ZZ-001")))
        assertEquals(listOf(HttpMethod.Patch, HttpMethod.Patch), requests.map { it.method })
        assertEquals(listOf("""{"displayName":"Wren Example"}""", """{"homeCounty":"US-ZZ-001"}"""), requests.map { it.text })
        requests.forEach { assertEquals(ContentType.Application.Json, (it.body as TextContent).contentType) }
    }

    @Test
    fun `deleting answers no content`() = runBlocking {
        val server = client { respond("", HttpStatusCode.NoContent) }

        assertEquals(ServerResult.Ok(Unit), server.deleteAccount(credentials))
        assertEquals(HttpMethod.Delete, requests.single().method)
        assertEquals("$BASE_URL/v1/account", requests.single().url.toString())
    }

    @Test
    fun `each error code of the account endpoints comes back typed`() = runBlocking {
        // docs/server/api.md: what the account endpoints can answer, with its status.
        val refusals = mapOf(
            ApiErrorCode.INVALID_REQUEST to HttpStatusCode.BadRequest,
            ApiErrorCode.UNAUTHORIZED to HttpStatusCode.Unauthorized,
            ApiErrorCode.DISPLAY_NAME_INVALID to HttpStatusCode.UnprocessableEntity,
            ApiErrorCode.COUNTY_UNKNOWN to HttpStatusCode.UnprocessableEntity,
            ApiErrorCode.RATE_LIMITED to HttpStatusCode.TooManyRequests,
            ApiErrorCode.SERVER_ERROR to HttpStatusCode.InternalServerError,
        )
        refusals.forEach { (code, status) ->
            val server = client { refusal(status, code.name) }
            val refused = ServerResult.Failed(ServerProblem.Refused(code))
            assertEquals("create, $code", refused, server.createAccount())
            assertEquals("profile, $code", refused, server.profile(credentials))
            assertEquals("update, $code", refused, server.updateProfile(credentials, UpdateProfileRequest(displayName = "Wren Example")))
            assertEquals("delete, $code", refused, server.deleteAccount(credentials))
        }
    }

    @Test
    fun `a code this build does not know is a general refusal`() = runBlocking {
        val server = client { refusal(HttpStatusCode.Conflict, "SOMETHING_NEWER") }

        assertEquals(ServerResult.Failed(ServerProblem.Refused(code = null)), server.deleteAccount(credentials))
    }

    @Test
    fun `a refusal that is not an ApiError is a general refusal`() = runBlocking {
        val server = client { respond("<html>Not found</html>", HttpStatusCode.NotFound) }

        assertEquals(ServerResult.Failed(ServerProblem.Refused(code = null)), server.createAccount())
    }

    @Test
    fun `a proxy answering for the server is no answer`() = runBlocking {
        listOf(HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout).forEach { status ->
            val server = client { respond("<html>down</html>", status) }
            assertEquals("$status", ServerResult.Failed(ServerProblem.NoAnswer), server.createAccount())
        }
    }

    @Test
    fun `a success whose body cannot be read is a general refusal`() = runBlocking {
        val server = client { json(HttpStatusCode.Created, """{"unexpected":true}""") }

        assertEquals(ServerResult.Failed(ServerProblem.Refused(code = null)), server.createAccount())
    }

    @Test
    fun `no network is its own problem`() = runBlocking {
        listOf(UnresolvedAddressException(), UnknownHostException("server.example")).forEach { thrown ->
            val server = client { throw thrown }
            assertEquals("$thrown", ServerResult.Failed(ServerProblem.NoNetwork), server.createAccount())
        }
    }

    @Test
    fun `a refused connection is no answer`() = runBlocking {
        val server = client { throw ConnectException("Connection refused") }

        assertEquals(ServerResult.Failed(ServerProblem.NoAnswer), server.profile(credentials))
    }

    @Test
    fun `a server that takes too long is no answer`() = runBlocking {
        val server = client(ServerTimeouts(requestMillis = 50)) {
            delay(10_000)
            respond("", HttpStatusCode.NoContent)
        }

        assertEquals(ServerResult.Failed(ServerProblem.NoAnswer), server.createAccount())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a plain http address is refused unless cleartext is allowed`() {
        ServerClient(serverHttpClient(MockEngine { respond("") }), "http://10.0.2.2:8080")
    }

    @Test
    fun `a plain http address is taken when cleartext is allowed`() = runBlocking {
        val engine = MockEngine { request ->
            requests += request
            respond("", HttpStatusCode.NoContent)
        }
        val server = ServerClient(serverHttpClient(engine), "http://10.0.2.2:8080", cleartextAllowed = true)

        assertEquals(ServerResult.Ok(Unit), server.deleteAccount(credentials))
        assertEquals("http://10.0.2.2:8080/v1/account", requests.single().url.toString())
    }

    @Test
    fun `credentials never print their secret`() {
        assertEquals(false, credentials.toString().contains(credentials.secret))
    }

    private companion object {
        const val BASE_URL = "https://server.example"
    }
}
