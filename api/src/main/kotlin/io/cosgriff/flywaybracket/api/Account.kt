package io.cosgriff.flywaybracket.api

import kotlinx.serialization.Serializable

/**
 * Asks for an anonymous account. It has no fields: ADR 0001 says the request that creates an account
 * carries nothing about the player. The display name and home county follow in [UpdateProfileRequest],
 * when the player sets them.
 */
@Serializable
data object CreateAccountRequest

/**
 * @property secret the only proof that the account belongs to this phone. Sent once; the app keeps it
 *   encrypted and sends it with each later request.
 */
@Serializable
data class CreateAccountResponse(val playerId: String, val secret: String)

/**
 * Sets or changes what the player has told the app about themselves. A field left out, or null, is not
 * changed.
 *
 * @property displayName the name the player's leagues see.
 * @property homeCounty the home county's code, such as `US-CO-059`. Other players never see it.
 */
@Serializable
data class UpdateProfileRequest(val displayName: String? = null, val homeCounty: String? = null)

/**
 * What the server holds about the asking player, as `GET /v1/account` returns it. Only that player is
 * ever given it: the home county is not shown to anyone else (ADR 0001).
 *
 * @property displayName null until the player sets it.
 * @property homeCounty null until the player sets it.
 * @property league the league the player is in, or null when they are in none: the one forming or in
 *   season, else their latest finished or closed one. [LeagueState.CLOSED] tells them their league never
 *   started, and [LeagueSummary.closedBecause] why.
 * @property homeCountyInPacks whether a pack the server holds has [homeCounty]; null while the home county
 *   is not set. When false, the player is left out of their league's seeding and is never eligible for a
 *   bird in their home county (#34).
 */
@Serializable
data class ProfileResponse(
    val displayName: String? = null,
    val homeCounty: String? = null,
    val league: LeagueSummary? = null,
    val homeCountyInPacks: Boolean? = null,
)
