package io.cosgriff.flywaybracket.server.sighting

import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.UploadSightingsRequest
import io.cosgriff.flywaybracket.server.account.SIGNED_IN
import io.cosgriff.flywaybracket.server.account.player
import io.cosgriff.flywaybracket.server.refuse
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.time.DateTimeException

private const val CLIENT_ID = "clientId"

/**
 * Reads an upload and refuses any key its type does not have. Every other message skips an unknown key
 * (`ApiJson`); a sighting may not, so that nothing beyond ADR 0001's fields, such as coordinates, is
 * accepted even to be thrown away.
 */
private val StrictJson = Json(ApiJson) { ignoreUnknownKeys = false }

/** `POST /v1/sightings` and `DELETE /v1/sightings/{clientId}` (docs/server/api.md). */
fun Route.sightingRoutes(sightings: Sightings) {
    authenticate(SIGNED_IN) {
        post(ApiPaths.SIGHTINGS) {
            // Read as JSON first, so a body that is not JSON is refused as for every other endpoint.
            val body = call.receive<JsonElement>()
            val request = try {
                StrictJson.decodeFromJsonElement(UploadSightingsRequest.serializer(), body)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            } catch (_: DateTimeException) {
                // A date that is not an ISO local date.
                null
            }
            if (request == null) {
                call.refuse(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
                return@post
            }
            when (withContext(Dispatchers.IO) { sightings.upload(player(), request) }) {
                UploadOutcome.STORED -> call.respond(HttpStatusCode.NoContent)
                UploadOutcome.TOO_MANY, UploadOutcome.ID_INVALID -> call.refuse(HttpStatusCode.BadRequest, ApiErrorCode.INVALID_REQUEST)
                UploadOutcome.COUNTY_UNKNOWN -> call.refuse(HttpStatusCode.UnprocessableEntity, ApiErrorCode.COUNTY_UNKNOWN)
                UploadOutcome.BIRD_UNKNOWN -> call.refuse(HttpStatusCode.UnprocessableEntity, ApiErrorCode.BIRD_UNKNOWN)
                UploadOutcome.DATE_IN_FUTURE -> call.refuse(HttpStatusCode.UnprocessableEntity, ApiErrorCode.SIGHTING_DATE_IN_FUTURE)
                UploadOutcome.NO_SUCH_PLAYER -> call.refuse(HttpStatusCode.Unauthorized, ApiErrorCode.UNAUTHORIZED)
            }
        }

        delete(ApiPaths.sighting("{$CLIENT_ID}")) {
            val clientId = call.parameters[CLIENT_ID].orEmpty()
            if (withContext(Dispatchers.IO) { sightings.delete(player(), clientId) }) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.refuse(HttpStatusCode.NotFound, ApiErrorCode.SIGHTING_NOT_FOUND)
            }
        }
    }
}
