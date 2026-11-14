package io.cosgriff.flywaybracket.data.server

import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.CreateAccountRequest
import io.cosgriff.flywaybracket.api.CreateAccountResponse
import io.cosgriff.flywaybracket.api.ProfileResponse
import io.cosgriff.flywaybracket.api.UpdateProfileRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import java.io.IOException
import java.net.NoRouteToHostException
import java.net.UnknownHostException
import java.nio.channels.UnresolvedAddressException
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException

/** How long a call may take before it counts as the server not answering. */
data class ServerTimeouts(val connectMillis: Long = 10_000, val socketMillis: Long = 15_000, val requestMillis: Long = 20_000)

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

/**
 * The league server's account endpoints (`docs/server/api.md`), with the messages of `:api`. Bodies are
 * written and read with `ApiJson`. Each call sends only what its message holds: ADR 0001's table lists
 * every field the app may send.
 *
 * @param baseUrl the server's address with no path and no slash at the end, such as `https://example.org`
 * @param cleartextAllowed whether a plain `http://` address is allowed: a debug build talking to a server
 *   on the developer's machine. A release build never is.
 */
class ServerClient(private val http: HttpClient, private val baseUrl: String, cleartextAllowed: Boolean = false) {
    init {
        require(baseUrl.startsWith("https://") || cleartextAllowed) { "The server's address must be https://" }
    }

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
    } catch (e: UnresolvedAddressException) {
        // The CIO engine's "could not look the name up": with no network there is no DNS.
        ServerResult.Failed(problemOf(e))
    } catch (e: IOException) {
        // Timeouts (Ktor's are IOExceptions), a refused or broken connection, a failed TLS handshake.
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
        val GATEWAY_FAILURES = setOf(HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout)

        /** `Authorization: Basic`, the id as the user and the secret as the password. */
        fun basic(credentials: Credentials): String {
            val pair = "${credentials.playerId}:${credentials.secret}".toByteArray(Charsets.UTF_8)
            return "Basic " + Base64.getEncoder().encodeToString(pair)
        }
    }
}

/** No network when the server's name could not be looked up or no route led to it; otherwise no answer. */
internal fun problemOf(e: Throwable): ServerProblem = when (e) {
    is UnresolvedAddressException, is UnknownHostException, is NoRouteToHostException -> ServerProblem.NoNetwork
    else -> ServerProblem.NoAnswer
}
