package io.cosgriff.flywaybracket.ui.components

import androidx.annotation.StringRes
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.data.server.ServerProblem

/**
 * The words for why a call to the server failed. They come from the problem's type and the server's error
 * code, never from the server's own text (#38). The league endpoints' codes have their own words (#40). A
 * code no screen can meet yet reads as a general failure until one gives it words.
 */
@get:StringRes
val ServerProblem.message: Int
    get() = when (this) {
        ServerProblem.NoNetwork -> R.string.server_problem_no_network
        ServerProblem.NoAnswer -> R.string.server_problem_no_answer
        is ServerProblem.Refused -> when (code) {
            ApiErrorCode.RATE_LIMITED -> R.string.server_problem_rate_limited
            ApiErrorCode.SERVER_ERROR -> R.string.server_problem_server_error
            ApiErrorCode.UNAUTHORIZED -> R.string.server_problem_unauthorized
            ApiErrorCode.DISPLAY_NAME_INVALID -> R.string.server_problem_display_name_invalid
            ApiErrorCode.COUNTY_UNKNOWN -> R.string.server_problem_county_unknown
            ApiErrorCode.LEAGUE_NOT_FOUND -> R.string.server_problem_league_not_found
            ApiErrorCode.INVITE_CODE_UNKNOWN -> R.string.server_problem_invite_code_unknown
            ApiErrorCode.LEAGUE_FULL -> R.string.server_problem_league_full
            ApiErrorCode.ALREADY_A_MEMBER -> R.string.server_problem_already_a_member
            ApiErrorCode.ALREADY_IN_A_LEAGUE -> R.string.server_problem_already_in_a_league
            ApiErrorCode.JOINING_CLOSED -> R.string.server_problem_joining_closed
            ApiErrorCode.LEAGUE_NAME_INVALID -> R.string.server_problem_league_name_invalid
            ApiErrorCode.SEASON_START_TOO_SOON -> R.string.server_problem_season_start_too_soon
            ApiErrorCode.SEASON_START_NOT_A_MONDAY -> R.string.server_problem_season_start_not_a_monday
            ApiErrorCode.TIME_ZONE_UNKNOWN -> R.string.server_problem_time_zone_unknown
            ApiErrorCode.PACK_NOT_FOUND -> R.string.server_problem_pack_not_found
            ApiErrorCode.INVALID_REQUEST,
            ApiErrorCode.BIRD_UNKNOWN,
            ApiErrorCode.PICKS_LOCKED,
            ApiErrorCode.PICKS_INCOMPLETE,
            ApiErrorCode.PICKS_HIDDEN,
            ApiErrorCode.MEMBER_NOT_FOUND,
            ApiErrorCode.BRACKET_NOT_READY,
            ApiErrorCode.ROUND_NOT_DECIDED,
            ApiErrorCode.SIGHTING_DATE_IN_FUTURE,
            ApiErrorCode.SIGHTING_NOT_FOUND,
            null,
            -> R.string.server_problem_general
        }
    }
