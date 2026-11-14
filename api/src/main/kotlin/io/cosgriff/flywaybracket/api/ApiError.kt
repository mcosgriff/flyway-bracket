package io.cosgriff.flywaybracket.api

import kotlinx.serialization.Serializable

/**
 * Every reason the server refuses a request. The app turns a code into words from `strings.xml`; the
 * server never sends wording. `docs/server/api.md` says which endpoint can return which.
 */
enum class ApiErrorCode {
    /** The request is not well-formed, or a field is outside what its type allows. */
    INVALID_REQUEST,

    /** The account id and secret are missing or wrong. */
    UNAUTHORIZED,

    /** No such league, or the player is not a member of it. The two are not told apart. */
    LEAGUE_NOT_FOUND,

    /** No league has this invite code. */
    INVITE_CODE_UNKNOWN,

    /** The league already has as many players as its capacity. */
    LEAGUE_FULL,

    /** The player is already in this league. */
    ALREADY_A_MEMBER,

    /** The player is already in a league that has not finished; a player is in one at a time. */
    ALREADY_IN_A_LEAGUE,

    /** The league's season has started, or the league is finished or closed: no one can join it. */
    JOINING_CLOSED,

    /** A league's name is empty, too short or too long. */
    LEAGUE_NAME_INVALID,

    /** A season starts on a Monday. */
    SEASON_START_NOT_A_MONDAY,

    /** A new league's season starts at least 7 days after the day it is created. */
    SEASON_START_TOO_SOON,

    /** The time zone is not one the server knows. */
    TIME_ZONE_UNKNOWN,

    /** The display name is empty, too long or not allowed. */
    DISPLAY_NAME_INVALID,

    /**
     * The county code is not one the server accepts: not a well-formed county code (a home county), or
     * not a county of any pack (a sighting).
     */
    COUNTY_UNKNOWN,

    /**
     * The bird is not one the server knows (a sighting), or not one of the birds that can play in the
     * matchup (a pick; [ApiError.slot] is the first such pick).
     */
    BIRD_UNKNOWN,

    /** A sighting's date is more than one day after today, wherever on Earth the player is. */
    SIGHTING_DATE_IN_FUTURE,

    /** The player has no sighting with this id. Another player's sighting is not told apart from none. */
    SIGHTING_NOT_FOUND,

    /** The season has started; picks can no longer change. */
    PICKS_LOCKED,

    /**
     * A later-round pick of a bird not picked to win every earlier matchup on its path. [ApiError.slot] is
     * the first such pick.
     */
    PICKS_INCOMPLETE,

    /** Another player's picks are hidden until they lock, when the season starts. */
    PICKS_HIDDEN,

    /** The league has no member with this key, or that member has left. */
    MEMBER_NOT_FOUND,

    /** The league has no bracket yet. */
    BRACKET_NOT_READY,

    /** The round is not decided yet. */
    ROUND_NOT_DECIDED,

    /** The server holds no pack for this state. */
    PACK_NOT_FOUND,

    /** Too many requests; try again later. */
    RATE_LIMITED,

    /** The server failed. Nothing the app sent was wrong. */
    SERVER_ERROR,
}

/**
 * The body of every refused request.
 *
 * @property code the name of an [ApiErrorCode]. Text, so that an app older than the server can still
 *   read an error whose code it does not know: see [errorCode].
 * @property detail for logs only, never shown to the player.
 * @property slot the matchup a refusal of picks is about: the first that cannot stand. Absent otherwise.
 */
@Serializable
data class ApiError(val code: String, val detail: String? = null, val slot: MatchupSlot? = null) {
    constructor(code: ApiErrorCode, detail: String? = null, slot: MatchupSlot? = null) : this(code.name, detail, slot)

    /** The code, or null when it is one this build does not know. */
    val errorCode: ApiErrorCode? get() = ApiErrorCode.entries.firstOrNull { it.name == code }
}
