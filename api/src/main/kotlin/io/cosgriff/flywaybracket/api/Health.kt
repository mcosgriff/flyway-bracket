package io.cosgriff.flywaybracket.api

import kotlinx.serialization.Serializable

/**
 * The server's answer to "are you up?". It says nothing about any player.
 *
 * @property version the server's version, such as `0.1.0`.
 * @property database whether the server's database answered just now.
 */
@Serializable
data class HealthResponse(val version: String, val database: Boolean)
