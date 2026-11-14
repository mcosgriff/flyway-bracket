package io.cosgriff.flywaybracket.ui

import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.PackListResponse
import io.cosgriff.flywaybracket.api.PackSummary
import io.cosgriff.flywaybracket.data.AccountRepository
import io.cosgriff.flywaybracket.data.PackDownloads
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.data.account.AccountStore
import io.cosgriff.flywaybracket.data.account.StoredAccount
import io.cosgriff.flywaybracket.data.packVersion
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.serverHttpClient
import io.cosgriff.flywaybracket.domain.pack.StatePack
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.net.UnknownHostException
import java.util.Collections
import kotlinx.coroutines.CoroutineScope

/**
 * A league server for ViewModel tests: Ktor's mock engine answers from [packs], and the account is kept in
 * memory. Every state, id and secret is made up.
 *
 * @param account whether the phone has an account to begin with
 */
class FakePackServer(installed: PackRepository, settings: SettingsRepository, scope: CoroutineScope, account: Boolean = true) {
    /** The packs the server holds, by state code. */
    val packs = mutableMapOf<String, ByteArray>()

    /** A size the list gives for a state in place of its file's, to stand for a large pack. */
    val listedSizes = mutableMapOf<String, Long>()

    /** When true, every call fails as with no network. */
    var offline = false

    val requests: MutableList<HttpRequestData> = Collections.synchronizedList(mutableListOf())

    val store = MemoryAccountStore(if (account) StoredAccount(PLAYER_ID, SECRET) else null)

    private val server = ServerClient(
        serverHttpClient(
            MockEngine { request ->
                requests += request
                if (offline) throw UnknownHostException("server.example")
                val path = request.url.encodedPath
                val code = path.removePrefix(ApiPaths.PACKS + "/")
                val file = packs[code]
                when {
                    path == ApiPaths.PACKS -> respond(
                        ApiJson.encodeToString(PackListResponse.serializer(), PackListResponse(summaries())),
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                    file != null -> respond(
                        file,
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.ETag to listOf("\"${packVersion(file)}\""),
                            HttpHeaders.ContentLength to listOf(file.size.toString()),
                        ),
                    )
                    else -> respond("", HttpStatusCode.NotFound)
                }
            },
        ),
        "https://server.example",
    )

    val accounts = AccountRepository(server, store, settings)
    val downloads = PackDownloads(server, accounts, installed, scope)

    /** Puts [pack] on the server, as its file. */
    fun hold(pack: StatePack): ByteArray = StatePackCodec.encode(pack).also { packs[pack.state.code] = it }

    fun summaries(): List<PackSummary> = packs.map { (code, bytes) ->
        val pack = (StatePackCodec.decode(bytes) as StatePackResult.Read).pack
        PackSummary(code, pack.state.name, listedSizes[code] ?: bytes.size.toLong(), pack.builtOn, packVersion(bytes))
    }.sortedBy { it.name }

    class MemoryAccountStore(var account: StoredAccount?) : AccountStore {
        override suspend fun read() = account

        override suspend fun write(account: StoredAccount) {
            this.account = account
        }

        override suspend fun clear() {
            account = null
        }
    }

    private companion object {
        const val PLAYER_ID = "00000000-0000-4000-8000-000000000003"
        const val SECRET = "made-up-secret"
    }
}
