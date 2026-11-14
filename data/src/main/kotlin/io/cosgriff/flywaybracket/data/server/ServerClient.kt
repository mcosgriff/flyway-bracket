package io.cosgriff.flywaybracket.data.server

import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.CreateAccountRequest
import io.cosgriff.flywaybracket.api.CreateAccountResponse
import io.cosgriff.flywaybracket.api.PackListResponse
import io.cosgriff.flywaybracket.api.ProfileResponse
import io.cosgriff.flywaybracket.api.UpdateProfileRequest
import io.cosgriff.flywaybracket.data.MAX_PACK_BYTES
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareRequest
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.security.cert.CertificateException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/** How long a call may take before it counts as the server not answering. */
data class ServerTimeouts(val connectMillis: Long = 10_000, val socketMillis: Long = 15_000, val requestMillis: Long = 20_000)

/**
 * The engine on the phone: OkHttp, so the platform's TLS and certificate checks and the app's network
 * security config (no cleartext outside debug builds) apply. OkHttp is told not to follow redirects
 * itself, as the client is ([serverHttpClient]).
 */
fun serverEngine(): HttpClientEngine = OkHttp.create {
    config {
        followRedirects(false)
        followSslRedirects(false)
    }
}

/**
 * The Ktor client the app talks to the server with. Redirects are not followed: the app talks to one
 * host (ADR 0001), and a redirect could name another.
 */
fun serverHttpClient(engine: HttpClientEngine, timeouts: ServerTimeouts = ServerTimeouts()): HttpClient = HttpClient(engine) {
    expectSuccess = false
    followRedirects = false
    install(HttpTimeout) {
        connectTimeoutMillis = timeouts.connectMillis
        socketTimeoutMillis = timeouts.socketMillis
        requestTimeoutMillis = timeouts.requestMillis
    }
}

/** The account id and secret, sent with each call that needs an account. Never logged. */
class Credentials(val playerId: String, val secret: String) {
    override fun toString() = "Credentials(playerId=$playerId)"
}

/** What asking for a pack file gave (#39). */
sealed interface PackFetch {
    /** The server's pack is the version the phone holds (`304 Not Modified`); nothing was sent. */
    data object Unchanged : PackFetch

    /**
     * The file, as it arrived. Not yet checked: it is a pack only once `StatePackCodec` reads it.
     *
     * @property version the version the server named in its `ETag`, or null when it named none
     */
    class Received(val bytes: ByteArray, val version: String?) : PackFetch

    /** Larger than [MAX_PACK_BYTES]: reading stopped there, and nothing was kept. */
    data object TooLarge : PackFetch
}

/**
 * The league server's account and pack endpoints (`docs/server/api.md`), with the messages of `:api`.
 * Bodies are written and read with `ApiJson`. Each call sends only what its message holds: ADR 0001's
 * table lists every field the app may send.
 *
 * @param baseUrl the server's address with no path and no slash at the end, such as `https://example.org`.
 *   Whether a plain `http://` one may connect is the network security config's to say, not this class's.
 * @param downloadMillis how long a pack download may take in all. A few megabytes on a slow connection
 *   take longer than any other call; a download that stalls still fails after the client's socket timeout.
 */
class ServerClient(
    private val http: HttpClient,
    private val baseUrl: String,
    private val downloadMillis: Long = DOWNLOAD_MILLIS,
) {

    /** `POST /v1/accounts`. The request carries nothing about the player: it is `{}`. */
    suspend fun createAccount(): ServerResult<CreateAccountResponse> = call(
        method = HttpMethod.Post,
        path = ApiPaths.ACCOUNTS,
        credentials = null,
        body = ApiJson.encodeToString(CreateAccountRequest.serializer(), CreateAccountRequest),
        answer = CreateAccountResponse.serializer(),
    )

    /** `GET /v1/account`: what the server holds about this player. */
    suspend fun profile(credentials: Credentials): ServerResult<ProfileResponse> =
        call(HttpMethod.Get, ApiPaths.ACCOUNT, credentials, body = null, answer = ProfileResponse.serializer())

    /** `PATCH /v1/account`. A field left null is not sent and not changed. */
    suspend fun updateProfile(credentials: Credentials, update: UpdateProfileRequest): ServerResult<Unit> = call(
        method = HttpMethod.Patch,
        path = ApiPaths.ACCOUNT,
        credentials = credentials,
        body = ApiJson.encodeToString(UpdateProfileRequest.serializer(), update),
        answer = null,
    )

    /** `DELETE /v1/account`. It cannot be undone. */
    suspend fun deleteAccount(credentials: Credentials): ServerResult<Unit> =
        call(HttpMethod.Delete, ApiPaths.ACCOUNT, credentials, body = null, answer = null)

    /** `GET /v1/packs`: the packs the server holds, in order of state name. */
    suspend fun packs(credentials: Credentials): ServerResult<PackListResponse> =
        call(HttpMethod.Get, ApiPaths.PACKS, credentials, body = null, answer = PackListResponse.serializer())

    /**
     * `GET /v1/packs/{state}`: the state's pack file, read as it arrives and no further than
     * [MAX_PACK_BYTES]. A connection lost on the way is a failure, never part of a file.
     *
     * @param stateCode the state's region code, such as `US-CO`
     * @param heldVersion the version of the state's pack on the phone, sent as `If-None-Match` so that an
     *   unchanged pack is not sent again; null when the phone has none
     * @param onProgress called as the file arrives, with the bytes so far and the file's size when the
     *   server gave it
     */
    suspend fun downloadPack(
        credentials: Credentials,
        stateCode: String,
        heldVersion: String?,
        onProgress: (receivedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): ServerResult<PackFetch> = try {
        http.prepareRequest {
            method = HttpMethod.Get
            url(baseUrl + ApiPaths.pack(stateCode))
            header(HttpHeaders.Authorization, basic(credentials))
            if (heldVersion != null) header(HttpHeaders.IfNoneMatch, "\"$heldVersion\"")
            timeout { requestTimeoutMillis = downloadMillis }
        }.execute { response ->
            when {
                response.status == HttpStatusCode.NotModified -> ServerResult.Ok(PackFetch.Unchanged)
                !response.status.isSuccess() -> ServerResult.Failed(refusal(response.status, response.bodyAsText()))
                else -> ServerResult.Ok(receive(response, onProgress))
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        ServerResult.Failed(problemOf(e))
    }

    private suspend fun receive(response: HttpResponse, onProgress: (Long, Long?) -> Unit): PackFetch {
        val total = response.contentLength()
        if (total != null && total > MAX_PACK_BYTES) return PackFetch.TooLarge
        val channel = response.bodyAsChannel()
        val bytes = ByteArrayOutputStream(total?.toInt() ?: DEFAULT_BUFFER_SIZE)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        onProgress(0, total)
        while (true) {
            // -1 at the end of the body. A connection lost on the way usually throws here, but Ktor's
            // readAvailable can also answer -1 for a channel closed with an error, so the end is checked below.
            val count = channel.readAvailable(buffer)
            if (count < 0) break
            bytes.write(buffer, 0, count)
            if (bytes.size() > MAX_PACK_BYTES) return PackFetch.TooLarge
            onProgress(bytes.size().toLong(), total)
        }
        // A body that ended in an error, or short of the size the server gave, was cut off: a lost connection
        // (caught by downloadPack), never a damaged file.
        channel.closedCause?.let { throw it as? IOException ?: IOException("The download failed", it) }
        if (total != null && bytes.size().toLong() != total) throw IOException("${bytes.size()} of $total bytes came")
        val version = response.headers[HttpHeaders.ETag]?.removePrefix(WEAK_TAG)?.removeSurrounding("\"")
        return PackFetch.Received(bytes.toByteArray(), version)
    }

    /** @param answer how to read a successful answer's body, or null when it has none worth reading */
    private suspend fun <T> call(
        method: HttpMethod,
        path: String,
        credentials: Credentials?,
        body: String?,
        answer: KSerializer<T>?,
    ): ServerResult<T> = try {
        val response = http.request {
            this.method = method
            url(baseUrl + path)
            if (credentials != null) header(HttpHeaders.Authorization, basic(credentials))
            if (body != null) setBody(TextContent(body, ContentType.Application.Json))
        }
        read(response, answer)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        // What OkHttp and Ktor throw: a name not found, timeouts (Ktor's are IOExceptions), a refused or
        // broken connection, a failed TLS handshake, a policy refusal.
        ServerResult.Failed(problemOf(e))
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> read(response: HttpResponse, answer: KSerializer<T>?): ServerResult<T> {
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) return ServerResult.Failed(refusal(response.status, text))
        if (answer == null) return ServerResult.Ok(Unit as T)
        return try {
            ServerResult.Ok(ApiJson.decodeFromString(answer, text))
        } catch (_: SerializationException) {
            // A success whose body this build cannot read: nothing the player can do but try again.
            ServerResult.Failed(ServerProblem.Refused(code = null))
        } catch (_: IllegalArgumentException) {
            ServerResult.Failed(ServerProblem.Refused(code = null))
        }
    }

    private fun refusal(status: HttpStatusCode, text: String): ServerProblem {
        val error = try {
            ApiJson.decodeFromString(ApiError.serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        return when {
            error != null -> ServerProblem.Refused(error.errorCode)
            // A proxy in front of the server answered for it: the server itself did not.
            status in GATEWAY_FAILURES -> ServerProblem.NoAnswer
            else -> ServerProblem.Refused(code = null)
        }
    }

    private companion object {
        /** Ten minutes: a pack of a few megabytes at a slow mobile data rate, and then some. */
        const val DOWNLOAD_MILLIS = 10 * 60_000L

        /** How a weak `ETag` starts (RFC 9110); the server sends strong ones. */
        const val WEAK_TAG = "W/"

        val GATEWAY_FAILURES = setOf(HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout)

        /** `Authorization: Basic`, the id as the user and the secret as the password. */
        fun basic(credentials: Credentials): String {
            val pair = "${credentials.playerId}:${credentials.secret}".toByteArray(Charsets.UTF_8)
            return "Basic " + Base64.getEncoder().encodeToString(pair)
        }
    }
}

/**
 * What a failed call means to the player:
 * - no network when the server's name could not be looked up (OkHttp's [UnknownHostException], which is
 *   what a phone with no connection gives) or no route led to it;
 * - a general failure when trying again cannot help: the network security config refused the address
 *   ([UnknownServiceException], cleartext), or the server's certificate was not accepted;
 * - otherwise no answer: a timeout, a refused or broken connection, a handshake cut short.
 */
internal fun problemOf(e: Throwable): ServerProblem = when {
    e is UnknownHostException || e is NoRouteToHostException -> ServerProblem.NoNetwork
    e is UnknownServiceException || e is SSLPeerUnverifiedException -> ServerProblem.Refused(code = null)
    e is SSLHandshakeException && e.causes().any { it is CertificateException } -> ServerProblem.Refused(code = null)
    else -> ServerProblem.NoAnswer
}

private fun Throwable.causes(): Sequence<Throwable> = generateSequence(cause) { it.cause.takeIf { next -> next !== it } }
