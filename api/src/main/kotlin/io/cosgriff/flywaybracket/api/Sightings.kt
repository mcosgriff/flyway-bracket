package io.cosgriff.flywaybracket.api

import kotlinx.serialization.Serializable

/**
 * One logged bird, as the server gets it. These four fields are all of it: ADR 0001 forbids
 * coordinates, the time of day and the place name the player typed, which stay on the phone.
 *
 * @property clientId an id the app makes for the sighting: a random UUID, in its usual text form. The
 *   server keeps one sighting per id for each player, so a retried upload is not counted twice. It is
 *   also how the app deletes the sighting ([ApiPaths.sighting]).
 * @property countyCode the county the bird was logged in, such as `US-CO-059`.
 * @property date the day of the sighting where the player was.
 */
@Serializable
data class SightingUpload(
    val clientId: String,
    val commonName: String,
    val countyCode: String,
    val date: ApiDate,
)

/** @property sightings at most [MAX_SIGHTINGS]; the app sends more in several requests. */
@Serializable
data class UploadSightingsRequest(val sightings: List<SightingUpload>) {
    companion object {
        /** The most sightings one request may carry. */
        const val MAX_SIGHTINGS = 500
    }
}
