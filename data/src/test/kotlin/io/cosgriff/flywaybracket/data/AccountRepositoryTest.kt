package io.cosgriff.flywaybracket.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.CreateAccountResponse
import io.cosgriff.flywaybracket.data.account.AccountStore
import io.cosgriff.flywaybracket.data.account.StoredAccount
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.data.server.ServerResult
import io.cosgriff.flywaybracket.data.server.serverHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import java.io.File
import java.net.UnknownHostException
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// The ids, secrets, names and county codes are made up. Ktor's mock engine stands in for the server.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AccountRepositoryTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val file = File(context.filesDir, "account_test.preferences_pb").apply { delete() }
    private val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
    private val store = MemoryAccountStore()

    private val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())

    /** What the server answers next, by method; a method with nothing set answers 204. */
    private var answer: (HttpRequestData) -> Answer = { request ->
        if (request.method == HttpMethod.Post) Answer.Created else Answer.NoContent
    }

    private val accounts = AccountRepository(
        server = ServerClient(
            serverHttpClient(
                MockEngine { request ->
                    requests += request
                    when (val next = answer(request)) {
                        Answer.Created -> respond(
                            ApiJson.encodeToString(CreateAccountResponse.serializer(), CreateAccountResponse(PLAYER_ID, SECRET)),
                            HttpStatusCode.Created,
                            JSON,
                        )
                        Answer.NoContent -> respond("", HttpStatusCode.NoContent)
                        is Answer.Refused -> respond(
                            ApiJson.encodeToString(ApiError.serializer(), ApiError(next.code)),
                            next.status,
                            JSON,
                        )
                        Answer.NoNetwork -> throw UnknownHostException("server.example")
                    }
                },
            ),
            "https://server.example",
        ),
        store = store,
        settings = settings,
    )

    private sealed interface Answer {
        data object Created : Answer

        data object NoContent : Answer

        data class Refused(val code: ApiErrorCode, val status: HttpStatusCode) : Answer

        data object NoNetwork : Answer
    }

    private class MemoryAccountStore : AccountStore {
        var account: StoredAccount? = null

        override suspend fun read() = account

        override suspend fun write(account: StoredAccount) {
            this.account = account
        }

        override suspend fun clear() {
            account = null
        }
    }

    private val HttpRequestData.text: String? get() = (body as? TextContent)?.text

    @After
    fun close() {
        scope.cancel()
    }

    @Test
    fun `with no account nothing is sent`() = runBlocking {
        settings.setDisplayName("Wren Example")
        settings.setHomeCountyCode("US-ZZ-001")

        assertFalse(accounts.hasAccount().first())
        assertEquals(ServerResult.Ok(Unit), accounts.syncProfile())
        assertEquals(ServerResult.Ok(Unit), accounts.deleteAccount())
        assertEquals(emptyList<HttpRequestData>(), requests)
    }

    @Test
    fun `creating keeps the account and then sends the profile`() = runBlocking {
        settings.setDisplayName("Wren Example")
        settings.setHomeCountyCode("US-ZZ-001")

        assertEquals(ServerResult.Ok(Unit), accounts.createAccount())

        assertTrue(accounts.hasAccount().first())
        assertEquals(StoredAccount(PLAYER_ID, SECRET, "Wren Example", "US-ZZ-001"), store.account)
        assertEquals(listOf(HttpMethod.Post, HttpMethod.Patch), requests.map { it.method })
        assertEquals("{}", requests[0].text)
        assertEquals("""{"displayName":"Wren Example","homeCounty":"US-ZZ-001"}""", requests[1].text)
    }

    @Test
    fun `creating again with an account makes no new one`() = runBlocking {
        accounts.createAccount()
        requests.clear()

        assertEquals(ServerResult.Ok(Unit), accounts.createAccount())
        assertEquals(emptyList<HttpMethod>(), requests.map { it.method })
    }

    @Test
    fun `a failed create keeps no account`() = runBlocking {
        answer = { Answer.NoNetwork }
        assertEquals(ServerResult.Failed(ServerProblem.NoNetwork), accounts.createAccount())

        answer = { Answer.Refused(ApiErrorCode.RATE_LIMITED, HttpStatusCode.TooManyRequests) }
        assertEquals(ServerResult.Failed(ServerProblem.Refused(ApiErrorCode.RATE_LIMITED)), accounts.createAccount())

        assertFalse(accounts.hasAccount().first())
        assertEquals(null, store.account)
    }

    @Test
    fun `a profile that fails to send is sent on the next sync`() = runBlocking {
        settings.setDisplayName("Wren Example")
        answer = { if (it.method == HttpMethod.Post) Answer.Created else Answer.NoNetwork }
        assertEquals(ServerResult.Ok(Unit), accounts.createAccount())
        assertEquals(null, store.account?.sentDisplayName)

        answer = { Answer.NoContent }
        assertEquals(ServerResult.Ok(Unit), accounts.syncProfile())
        assertEquals("Wren Example", store.account?.sentDisplayName)
        assertEquals("""{"displayName":"Wren Example"}""", requests.last().text)
    }

    @Test
    fun `only what changed is sent, and nothing when nothing did`() = runBlocking {
        settings.setDisplayName("Wren Example")
        settings.setHomeCountyCode("US-ZZ-001")
        accounts.createAccount()
        requests.clear()

        assertEquals(ServerResult.Ok(Unit), accounts.syncProfile())
        assertEquals(emptyList<HttpRequestData>(), requests)

        settings.setHomeCountyCode("US-ZZ-003")
        assertEquals(ServerResult.Ok(Unit), accounts.syncProfile())
        assertEquals("""{"homeCounty":"US-ZZ-003"}""", requests.single().text)
    }

    @Test
    fun `a refused profile is kept as unsent`() = runBlocking {
        accounts.createAccount()
        answer = { Answer.Refused(ApiErrorCode.DISPLAY_NAME_INVALID, HttpStatusCode.UnprocessableEntity) }
        settings.setDisplayName("Wren Example")

        assertEquals(ServerResult.Failed(ServerProblem.Refused(ApiErrorCode.DISPLAY_NAME_INVALID)), accounts.syncProfile())
        assertEquals(null, store.account?.sentDisplayName)
    }

    @Test
    fun `a change in the settings is sent while the profile is kept in sync`() = runBlocking {
        accounts.createAccount()
        requests.clear()
        val sync = launch(Dispatchers.Default) { accounts.keepProfileInSync() }

        settings.setDisplayName("Wren Example")
        withTimeout(5_000) {
            while (store.account?.sentDisplayName != "Wren Example") yield()
        }
        sync.cancelAndJoin()
        assertEquals("""{"displayName":"Wren Example"}""", requests.single().text)
    }

    @Test
    fun `deleting clears the account from the phone`() = runBlocking {
        accounts.createAccount()

        assertEquals(ServerResult.Ok(Unit), accounts.deleteAccount())
        assertEquals(HttpMethod.Delete, requests.last().method)
        assertFalse(accounts.hasAccount().first())
        assertEquals(null, store.account)
    }

    @Test
    fun `an account the server no longer knows is cleared from the phone too`() = runBlocking {
        accounts.createAccount()
        answer = { Answer.Refused(ApiErrorCode.UNAUTHORIZED, HttpStatusCode.Unauthorized) }

        assertEquals(ServerResult.Ok(Unit), accounts.deleteAccount())
        assertFalse(accounts.hasAccount().first())
    }

    @Test
    fun `a delete that does not reach the server keeps the account`() = runBlocking {
        accounts.createAccount()
        answer = { Answer.NoNetwork }

        assertEquals(ServerResult.Failed(ServerProblem.NoNetwork), accounts.deleteAccount())
        assertTrue(accounts.hasAccount().first())
        assertEquals(PLAYER_ID, store.account?.playerId)
    }

    @Test
    fun `an account already on the phone is read from the store`() = runBlocking {
        store.account = StoredAccount(PLAYER_ID, SECRET)
        assertTrue(accounts.hasAccount().first())
    }

    private companion object {
        const val PLAYER_ID = "00000000-0000-4000-8000-000000000001"
        const val SECRET = "made-up-secret"
        val JSON = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
    }
}
