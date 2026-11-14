package io.cosgriff.flywaybracket.server.pack

import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.PackListResponse
import io.cosgriff.flywaybracket.server.account.SIGNED_IN
import io.cosgriff.flywaybracket.server.refuse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val STATE = "state"

// The file needs an account, so no shared cache may keep it; and the app asks again each time, with the
// version it holds.
private const val CACHE_CONTROL = "private, no-cache"

/** `GET /v1/packs` and `GET /v1/packs/{state}` (docs/server/api.md). */
fun Route.packRoutes(packs: Packs) {
    authenticate(SIGNED_IN) {
        get(ApiPaths.PACKS) {
            // A look at the directory, and maybe a file read, block: off the request threads.
            call.respond(PackListResponse(withContext(Dispatchers.IO) { packs.summaries() }))
        }

        get(ApiPaths.pack("{$STATE}")) {
            val held = withContext(Dispatchers.IO) { packs.file(call.parameters[STATE].orEmpty()) }
            if (held == null) {
                call.refuse(HttpStatusCode.NotFound, ApiErrorCode.PACK_NOT_FOUND)
                return@get
            }
            val etag = entityTag(held.version)
            call.response.header(HttpHeaders.ETag, etag)
            call.response.header(HttpHeaders.CacheControl, CACHE_CONTROL)
            if (noneMatch(call.request.headers.getAll(HttpHeaders.IfNoneMatch), etag)) {
                call.respondBytes(held.bytes, ContentType.Application.OctetStream)
            } else {
                call.respond(HttpStatusCode.NotModified)
            }
        }
    }
}

/** The `ETag` of a pack file of this version: the version, quoted. */
internal fun entityTag(version: String): String = "\"$version\""

/**
 * Whether no tag of the `If-None-Match` headers matches [etag], so the file must be sent. `*` matches any
 * file; a weak tag (`W/"…"`) matches its strong one, as RFC 9110 has it for this header.
 */
internal fun noneMatch(headers: List<String>?, etag: String): Boolean {
    val tags = headers.orEmpty().flatMap { it.split(',') }.map { it.trim().removePrefix(WEAK) }
    return tags.none { it == ANY || it == etag }
}

private const val WEAK = "W/"
private const val ANY = "*"
