package io.cosgriff.flywaybracket.api

import io.cosgriff.flywaybracket.domain.bracket.Round

/**
 * The path of every endpoint (`docs/server/api.md`). The app calls a function with an id; the server
 * calls it with a route parameter (`ApiPaths.league("{leagueId}")`), so both read one definition.
 */
object ApiPaths {
    const val V1 = "/v1"

    const val HEALTH = "$V1/health"
    const val ACCOUNTS = "$V1/accounts"
    const val ACCOUNT = "$V1/account"
    const val LEAGUES = "$V1/leagues"
    const val JOIN_LEAGUE = "$V1/leagues/join"
    const val AUTO_JOIN_LEAGUE = "$V1/leagues/auto-join"
    const val SIGHTINGS = "$V1/sightings"
    const val PACKS = "$V1/packs"

    fun league(leagueId: String): String = "$LEAGUES/$leagueId"

    /** @param stateCode a state's region code, such as `US-CO`, or the server's route parameter. */
    fun pack(stateCode: String): String = "$PACKS/$stateCode"

    /** @param clientId a sighting's [SightingUpload.clientId], or the server's route parameter. */
    fun sighting(clientId: String): String = "$SIGHTINGS/$clientId"

    fun membership(leagueId: String): String = "${league(leagueId)}/membership"

    fun bracket(leagueId: String): String = "${league(leagueId)}/bracket"

    fun picks(leagueId: String): String = "${league(leagueId)}/picks"

    fun standings(leagueId: String): String = "${league(leagueId)}/standings"

    /** @param round a [Round]'s name, such as `ROUND_OF_64`, or the server's route parameter. */
    fun roundResults(leagueId: String, round: String): String = "${league(leagueId)}/rounds/$round/results"

    fun roundResults(leagueId: String, round: Round): String = roundResults(leagueId, round.name)
}
