package io.cosgriff.flywaybracket.server

import io.cosgriff.flywaybracket.api.ApiError
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.HealthResponse
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.ContentTransformationException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The whole server: JSON as `:api` defines it, every failure as an [ApiError], one log line per request,
 * and the routes.
 *
 * @param database asked by the health check
 * @param version what the health check reports
 */
fun Application.module(database: DatabaseCheck, version: String) {
    install(ContentNegotiation) { json(ApiJson) }
    install(CallLogging) { format(::requestLogLine) }
    install(StatusPages) {
        // A body that is not the JSON its type asks for. The cause is not logged and not sent back: its
        // message quotes the body.
        exception<BadRequestException> { call, _ ->
            call.respondError(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
        }
        // A body that is not JSON at all.
        exception<ContentTransformationException> { call, _ ->
            call.respondError(HttpStatusCode.UnsupportedMediaType, ApiErrorCode.INVALID_REQUEST)
        }
        exception<Throwable> { call, cause ->
            call.application.log.error("Request failed: ${call.request.httpMethod.value} ${call.request.path()}", cause)
            call.respondError(HttpStatusCode.InternalServerError, ApiErrorCode.SERVER_ERROR)
        }
        // A path or a method the server does not have. The status says which; the code is the nearest of
        // the fixed list.
        status(HttpStatusCode.NotFound, HttpStatusCode.MethodNotAllowed, HttpStatusCode.UnsupportedMediaType) { call, status ->
            call.respondError(status, ApiErrorCode.INVALID_REQUEST)
        }
    }

    routing {
        get(ApiPaths.HEALTH) {
            // JDBC blocks, so the question is asked off the request threads.
            val answers = withContext(Dispatchers.IO) { database.answers() }
            call.respond(HealthResponse(version = version, database = answers))
        }
    }
}

/**
 * What the log says of a request: its status, method and path. Never the query, a header or the body: the
 * `Authorization` header carries the player's secret.
 */
internal fun requestLogLine(call: ApplicationCall): String {
    val status = call.response.status()?.value ?: "-"
    return "$status ${call.request.httpMethod.value} ${call.request.path()}"
}

private suspend fun ApplicationCall.respondError(status: HttpStatusCode, code: ApiErrorCode) {
    respond(status, ApiError(code))
}
