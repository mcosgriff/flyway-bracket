package io.cosgriff.flywaybracket.server.account

import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.CreateAccountRequest
import io.cosgriff.flywaybracket.api.UpdateProfileRequest
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationFailedCause
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.request.ApplicationRequest
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Base64

/** The name of the check that a request is signed by a player. */
const val SIGNED_IN = "player"

/**
 * Reads the account id and secret from `Authorization: Basic` (the id as the user, the secret as the
 * password). A request with none, a wrong one, or a header that cannot be read is refused with 401 and an
 * [ApiError] before its route runs.
 */
fun Application.installSignIn(accounts: Accounts) {
    install(Authentication) {
        provider(SIGNED_IN) {
            authenticate { context ->
                val credentials = basicCredentials(context.call.request.headers[HttpHeaders.Authorization])
                // The hash takes tens of milliseconds and the lookup blocks, so both run off the request threads.
                val player = credentials?.let { (id, secret) -> withContext(Dispatchers.IO) { accounts.authenticate(id, secret) } }
                if (player != null) {
                    context.principal(player)
                } else {
                    context.challenge(SIGNED_IN, AuthenticationFailedCause.InvalidCredentials) { challenge, call ->
                        call.response.header(HttpHeaders.WWWAuthenticate, CHALLENGE)
                        call.respondUnauthorized()
                        challenge.complete()
                    }
                }
            }
        }
    }
}

/**
 * The user and password of an `Authorization: Basic` header, or null when there is none or it cannot be
 * read. Never throws: a malformed header is a refusal like a wrong secret, not a bad request.
 */
internal fun basicCredentials(header: String?): Pair<String, String>? {
    if (header == null) return null
    val scheme = header.substringBefore(' ')
    if (!scheme.equals(BASIC, ignoreCase = true)) return null
    val decoded = try {
        Base64.getDecoder().decode(header.substringAfter(' ').trim())
    } catch (_: IllegalArgumentException) {
        return null
    }
    val text = decoded.toString(Charsets.UTF_8)
    val colon = text.indexOf(':')
    if (colon < 0) return null
    return text.substring(0, colon) to text.substring(colon + 1)
}

/**
 * The caller's address, for the account creation limit only: counted in memory, never stored and never
 * logged.
 *
 * @param trustedHeader the header a proxy in front of the server sets on every request, overwriting any
 *   the caller sent (`ServerConfig.clientAddressHeader`); null when there is none, as on a developer's
 *   machine. Without it, a header the caller sent is ignored and the connection's address is used.
 */
internal fun clientAddress(request: ApplicationRequest, trustedHeader: String?): String {
    val fromProxy = trustedHeader?.let { request.headers[it] }?.trim()?.takeIf { it.isNotEmpty() }
    return fromProxy ?: request.origin.remoteAddress
}

/**
 * `POST /v1/accounts`, and `GET`, `PATCH` and `DELETE` on `/v1/account` (docs/server/api.md).
 *
 * @param clientAddressHeader where the caller's address is read from; see [clientAddress]
 */
fun Route.accountRoutes(accounts: Accounts, clientAddressHeader: String? = null) {
    post(ApiPaths.ACCOUNTS) {
        // The address is only counted, in memory, for the rate limit; it is not stored with the account.
        val address = clientAddress(call.request, clientAddressHeader)
        call.receive<CreateAccountRequest>()
        val created = withContext(Dispatchers.IO) { accounts.create(address) }
        if (created == null) {
            call.respond(HttpStatusCode.TooManyRequests, ApiError(ApiErrorCode.RATE_LIMITED))
        } else {
            call.respond(HttpStatusCode.Created, created)
        }
    }

    authenticate(SIGNED_IN) {
        get(ApiPaths.ACCOUNT) {
            val profile = withContext(Dispatchers.IO) { accounts.profile(player()) }
            if (profile == null) call.respondUnauthorized() else call.respond(profile)
        }

        patch(ApiPaths.ACCOUNT) {
            val request = call.receive<UpdateProfileRequest>()
            when (withContext(Dispatchers.IO) { accounts.update(player(), request) }) {
                ProfileUpdate.Updated -> call.respond(HttpStatusCode.NoContent)
                ProfileUpdate.DisplayNameInvalid ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ApiError(ApiErrorCode.DISPLAY_NAME_INVALID))
                ProfileUpdate.HomeCountyInvalid ->
                    call.respond(HttpStatusCode.UnprocessableEntity, ApiError(ApiErrorCode.COUNTY_UNKNOWN))
                ProfileUpdate.NoSuchPlayer -> call.respondUnauthorized()
            }
        }

        delete(ApiPaths.ACCOUNT) {
            withContext(Dispatchers.IO) { accounts.delete(player()) }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private const val BASIC = "Basic"
private const val CHALLENGE = "$BASIC realm=\"flyway-bracket\", charset=\"UTF-8\""

/** The player who signed the request; only inside [authenticate], where there always is one. */
internal fun RoutingContext.player(): SignedInPlayer = checkNotNull(call.principal<SignedInPlayer>())

private suspend fun ApplicationCall.respondUnauthorized() {
    respond(HttpStatusCode.Unauthorized, ApiError(ApiErrorCode.UNAUTHORIZED))
}
