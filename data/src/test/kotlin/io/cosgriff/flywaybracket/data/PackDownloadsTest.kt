package io.cosgriff.flywaybracket.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.PackListResponse
import io.cosgriff.flywaybracket.api.PackSummary
import io.cosgriff.flywaybracket.data.account.AccountStore
import io.cosgriff.flywaybracket.data.account.StoredAccount
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.data.server.serverHttpClient
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import io.cosgriff.flywaybracket.domain.pack.testStatePack
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeFully
import java.io.File
import java.io.IOException
import java.time.LocalDate
import java.util.Base64
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Every pack, id and secret is made up: Testland from the shared fixture. Ktor's mock engine is the server.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PackDownloadsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val settings = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(context.filesDir, "downloads_test.preferences_pb").apply { delete() } },
    )
    private val store = MemoryAccountStore(StoredAccount(PLAYER_ID, SECRET))
    private val packs = PackRepository(File(context.filesDir, "downloads_test").apply { deleteRecursively() })

    private val testland = testStatePack()
    private val testlandBytes = StatePackCodec.encode(testland)

    /** The same state, built again a month later: another file, so another version. */
    private val newerTestland = testland.copy(builtOn = testland.builtOn.plusMonths(1))
    private val newerBytes = StatePackCodec.encode(newerTestland)

    private val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())

    /** How the server answers. */
    private var answer: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { file(newerBytes) }

    private val downloads = PackDownloads(
        server = ServerClient(
            serverHttpClient(
                MockEngine { request ->
                    requests += request
                    answer(request)
                },
            ),
            BASE_URL,
        ),
        accounts = AccountRepository(serverOf { respond("", HttpStatusCode.NoContent) }, store, settings),
        packs = packs,
        scope = scope,
    )

    private fun serverOf(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        ServerClient(serverHttpClient(MockEngine { handler(it) }), BASE_URL)

    private class MemoryAccountStore(var account: StoredAccount?) : AccountStore {
        override suspend fun read() = account

        override suspend fun write(account: StoredAccount) {
            this.account = account
        }

        override suspend fun clear() {
            account = null
        }
    }

    /** A pack file as the server sends it: its bytes, with their version as the `ETag`. */
    private fun MockRequestHandleScope.file(bytes: ByteArray, etag: String = packVersion(bytes)) = respond(
        bytes,
        HttpStatusCode.OK,
        headersOf(
            HttpHeaders.ContentType to listOf(ContentType.Application.OctetStream.toString()),
            HttpHeaders.ETag to listOf("\"$etag\""),
            HttpHeaders.ContentLength to listOf(bytes.size.toString()),
        ),
    )

    private fun MockRequestHandleScope.refusal(status: HttpStatusCode, code: ApiErrorCode) = respond(
        ApiJson.encodeToString(ApiError.serializer(), ApiError(code)),
        status,
        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private suspend fun installedVersion(): String? = packs.installed().first().singleOrNull()?.version

    @After
    fun close() {
        scope.cancel()
    }

    @Test
    fun `the server's packs are listed, asked for with the account`() = runBlocking {
        val summary = PackSummary("XX-TS", "Testland", 2_048, LocalDate.of(2027, 3, 1), "ab12")
        answer = {
            respond(
                ApiJson.encodeToString(PackListResponse.serializer(), PackListResponse(listOf(summary))),
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

        assertEquals(PackListing.Listed(listOf(summary)), downloads.available())
        val sent = requests.single()
        assertEquals(HttpMethod.Get, sent.method)
        assertEquals("$BASE_URL/v1/packs", sent.url.toString())
        assertEquals(basic(), sent.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `a list the server cannot give says why`() = runBlocking {
        answer = { throw IOException("connection reset") }
        assertEquals(PackListing.Failed(ServerProblem.NoAnswer), downloads.available())
    }

    @Test
    fun `with no account nothing is asked, for the list or a pack`() = runBlocking {
        store.account = null

        assertEquals(PackListing.NoAccount, downloads.available())
        assertEquals(PackDownload.NoAccount, downloads.download("XX-TS", version = null, sizeBytes = null))
        assertEquals(emptyList<HttpRequestData>(), requests)
    }

    @Test
    fun `a pack is downloaded with its progress and installed`() = runBlocking {
        answer = { file(testlandBytes) }
        val progress = mutableListOf<DownloadProgress>()

        val result = downloads.download("XX-TS", packVersion(testlandBytes), testlandBytes.size.toLong()) { progress += it }

        val installed = InstalledPack(testland, testlandBytes.size.toLong(), packVersion(testlandBytes))
        assertEquals(PackDownload.Installed(installed), result)
        assertEquals(listOf(installed), packs.installed().first())
        val sent = requests.single()
        assertEquals("$BASE_URL/v1/packs/XX-TS", sent.url.toString())
        assertEquals(basic(), sent.headers[HttpHeaders.Authorization])
        // With no pack on the phone there is no version to send.
        assertNull(sent.headers[HttpHeaders.IfNoneMatch])
        assertEquals(DownloadProgress(0, testlandBytes.size.toLong()), progress.first())
        assertEquals(DownloadProgress(testlandBytes.size.toLong(), testlandBytes.size.toLong()), progress.last())
        assertEquals(1f, progress.last().fraction)
    }

    @Test
    fun `an update sends the version on the phone and replaces the pack`() = runBlocking {
        packs.install(testlandBytes)

        val result = downloads.download("XX-TS", packVersion(newerBytes), newerBytes.size.toLong())

        assertTrue(result is PackDownload.Installed)
        assertEquals("\"${packVersion(testlandBytes)}\"", requests.single().headers[HttpHeaders.IfNoneMatch])
        assertEquals(packVersion(newerBytes), installedVersion())
        assertEquals(newerTestland.builtOn, packs.installed().first().single().pack.builtOn)
    }

    @Test
    fun `a pack the server has not changed is not sent again`() = runBlocking {
        packs.install(testlandBytes)
        answer = { respond(ByteArray(0), HttpStatusCode.NotModified, headersOf(HttpHeaders.ETag, "\"${packVersion(testlandBytes)}\"")) }

        assertEquals(PackDownload.Unchanged, downloads.download("XX-TS", packVersion(testlandBytes), null))
        assertEquals(packVersion(testlandBytes), installedVersion())
    }

    @Test
    fun `a download cut off on the way leaves the old pack in place`() = runBlocking {
        packs.install(testlandBytes)
        // Half the file, then the connection fails. The channel is filled and closed before the client reads
        // it, so no writer races the reader: when one did, Ktor sometimes handed the failure on as a clean
        // end of the body (CI, run 43572404733). The whole file's Content-Length goes with it, as the server
        // sends one (respondBytes), so the client tells a cut-off body from a damaged one either way.
        answer = {
            val body = ByteChannel()
            body.writeFully(newerBytes, 0, newerBytes.size / 2)
            body.close(IOException("connection lost"))
            respond(
                body,
                HttpStatusCode.OK,
                headersOf(
                    HttpHeaders.ETag to listOf("\"${packVersion(newerBytes)}\""),
                    HttpHeaders.ContentLength to listOf(newerBytes.size.toString()),
                ),
            )
        }

        val result = downloads.download("XX-TS", packVersion(newerBytes), newerBytes.size.toLong())

        assertEquals(PackDownload.Failed(ServerProblem.NoAnswer), result)
        assertOldPackInPlace()
    }

    @Test
    fun `a file shorter than the size the server gave is a cut-off download, not a damaged one`() = runBlocking {
        packs.install(testlandBytes)
        // Half the file, ended cleanly, under the whole file's Content-Length.
        answer = {
            respond(
                newerBytes.copyOf(newerBytes.size / 2),
                HttpStatusCode.OK,
                headersOf(
                    HttpHeaders.ETag to listOf("\"${packVersion(newerBytes)}\""),
                    HttpHeaders.ContentLength to listOf(newerBytes.size.toString()),
                ),
            )
        }

        val result = downloads.download("XX-TS", packVersion(newerBytes), newerBytes.size.toLong())

        assertEquals(PackDownload.Failed(ServerProblem.NoAnswer), result)
        assertOldPackInPlace()
    }

    private suspend fun assertOldPackInPlace() {
        assertEquals(packVersion(testlandBytes), installedVersion())
        assertEquals(packVersion(testlandBytes), packVersion(File(context.filesDir, "downloads_test/XX-TS.fbpack").readBytes()))
    }

    @Test
    fun `a download the server refuses leaves the old pack in place and says why`() = runBlocking {
        packs.install(testlandBytes)
        answer = { refusal(HttpStatusCode.NotFound, ApiErrorCode.PACK_NOT_FOUND) }

        val result = downloads.download("XX-TS", packVersion(newerBytes), null)

        assertEquals(PackDownload.Failed(ServerProblem.Refused(ApiErrorCode.PACK_NOT_FOUND)), result)
        assertEquals(packVersion(testlandBytes), installedVersion())
    }

    @Test
    fun `a corrupt file is refused and the old pack stays`() = runBlocking {
        packs.install(testlandBytes)
        // Its ETag matches it, so it came as the server sent it: it is the file itself that is no pack.
        val corrupt = newerBytes.copyOf(newerBytes.size / 2)
        answer = { file(corrupt) }

        val result = downloads.download("XX-TS", packVersion(corrupt), corrupt.size.toLong())

        assertTrue(result is PackDownload.Refused && result.reason != null)
        assertEquals(packVersion(testlandBytes), installedVersion())
    }

    @Test
    fun `a file that is not the version the server named is refused`() = runBlocking {
        answer = { file(newerBytes, etag = packVersion(testlandBytes)) }

        assertEquals(PackDownload.Refused(reason = null), downloads.download("XX-TS", packVersion(testlandBytes), null))
        assertEquals(emptyList<InstalledPack>(), packs.installed().first())
    }

    @Test
    fun `a file larger than any pack is turned away before it is read`() = runBlocking {
        answer = { respond(ByteArray(0), HttpStatusCode.OK, headersOf(HttpHeaders.ContentLength, "${MAX_PACK_BYTES + 1L}")) }

        assertEquals(PackDownload.Refused(reason = null), downloads.download("XX-TS", null, null))
    }

    @Test
    fun `a pack format this app does not read is refused with that reason`() = runBlocking {
        val notAPack = "not a pack".toByteArray()
        answer = { file(notAPack) }

        assertEquals(PackDownload.Refused(StatePackResult.Reason.NOT_GZIP), downloads.download("XX-TS", null, null))
    }

    @Test
    fun `a started download is followed in the state, and a failed one is kept until the state is tried again`() = runBlocking {
        val summary = PackSummary("XX-TS", "Testland", newerBytes.size.toLong(), newerTestland.builtOn, packVersion(newerBytes))
        answer = { throw IOException("connection reset") }

        downloads.start(summary)
        val failed = withTimeout(10_000) { downloads.state.first { it.failed != null } }
        assertEquals(FailedDownload("XX-TS", PackDownload.Failed(ServerProblem.NoAnswer)), failed.failed)
        assertEquals(emptyMap<String, DownloadProgress>(), failed.running)

        answer = { file(newerBytes) }
        downloads.start(summary)
        withTimeout(10_000) { downloads.state.first { it.failed == null && it.running.isEmpty() } }
        assertEquals(packVersion(newerBytes), withTimeout(10_000) { packs.installed().first { it.isNotEmpty() } }.single().version)
    }

    @Test
    fun `a download over 5 MB asks first on a metered connection, and only there`() {
        assertTrue(asksBeforeDownload(5_000_001, metered = true))
        assertEquals(false, asksBeforeDownload(5_000_000, metered = true))
        assertEquals(false, asksBeforeDownload(50_000_000, metered = false))
    }

    @Test
    fun `progress is a fraction only when the size is known`() {
        assertEquals(0.5f, DownloadProgress(512, 1_024).fraction)
        assertNull(DownloadProgress(512, null).fraction)
        assertNull(DownloadProgress(0, 0).fraction)
    }

    private fun basic() = "Basic " + Base64.getEncoder().encodeToString("$PLAYER_ID:$SECRET".toByteArray())

    private companion object {
        const val BASE_URL = "https://server.example"
        const val PLAYER_ID = "00000000-0000-4000-8000-000000000002"
        const val SECRET = "made-up-secret"
    }
}
