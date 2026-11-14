package io.cosgriff.flywaybracket.api

import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.league.CloseReason
import kotlinx.serialization.Serializable

/**
 * @property open whether the league is open, as the player chose when creating it.
 * @property timeZone an IANA zone id, such as `America/Denver`. Every date of the league's season is a
 *   date in this zone.
 * @property seasonStart the first Monday of the season; picks lock when it begins.
 */
@Serializable
data class CreateLeagueRequest(
    val name: String,
    val open: Boolean,
    val timeZone: String,
    val seasonStart: ApiDate,
)

/** Where a league is in its season. */
enum class LeagueState {
    /** The season has not started: players can join and change their picks. */
    BEFORE_SEASON,

    /** Picks are locked and rounds are being played. */
    IN_SEASON,

    /** The final is decided. */
    FINISHED,

    /**
     * The season never started ([LeagueSummary.closedBecause] says why): the league had fewer than 8
     * players when its start date arrived, and still did after the start had moved back a week four
     * times; or too few migrating birds pass through its players' home counties to seed a field of 64. Its
     * players can join another league.
     */
    CLOSED,
}

/**
 * A league as a list shows it.
 *
 * @property inviteCode what another player types to join; null when the server does not give it to this
 *   player.
 * @property players how many players are in the league now.
 * @property capacity the most players the league can hold.
 * @property closedBecause why a [LeagueState.CLOSED] league never started; null in every other state.
 */
@Serializable
data class LeagueSummary(
    val id: String,
    val name: String,
    val inviteCode: String? = null,
    val players: Int,
    val capacity: Int,
    val state: LeagueState,
    val closedBecause: CloseReason? = null,
)

/** The leagues the player is in: at most one forming or in season, and any finished or closed. */
@Serializable
data class LeagueListResponse(val leagues: List<LeagueSummary>)

@Serializable
data class JoinLeagueRequest(val inviteCode: String)

/**
 * One player's place in a league. It holds only what ADR 0001 lets the others see: no county, no
 * sightings, and no account id.
 *
 * @property rank 1 is first. Players level on points and on [rightPicks] share a rank.
 * @property memberKey names this member in the league's paths, such as [ApiPaths.memberPicks]: made by the
 *   server when the player joins, one per league, and never the account id.
 * @property you whether this is the player who asked.
 * @property rightPicks how many decided matchups the player picked the winner of; it breaks a tie on points.
 */
@Serializable
data class Standing(
    val displayName: String,
    val points: Int,
    val rank: Int,
    val memberKey: String,
    val you: Boolean = false,
    val rightPicks: Int = 0,
)

/**
 * A league, its members and its season.
 *
 * @property seasonStart the first Monday. The six rounds are the six weeks from it
 *   (`SeasonCalendar` in `:domain` gives each round's dates).
 * @property seasonEnd the Sunday of the final.
 * @property members best rank first.
 */
@Serializable
data class LeagueDetail(
    val league: LeagueSummary,
    val season: Season,
    val timeZone: String,
    val seasonStart: ApiDate,
    val seasonEnd: ApiDate,
    val members: List<Standing>,
)
