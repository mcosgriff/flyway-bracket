package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.CreateLeagueRequest
import io.cosgriff.flywaybracket.api.JoinLeagueRequest
import io.cosgriff.flywaybracket.api.LeagueListResponse
import io.cosgriff.flywaybracket.server.account.SIGNED_IN
import io.cosgriff.flywaybracket.server.account.player
import io.cosgriff.flywaybracket.server.refuse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val LEAGUE_ID = "leagueId"

/**
 * `POST /v1/leagues`, `GET /v1/leagues`, `POST /v1/leagues/join`, `POST /v1/leagues/auto-join`,
 * `GET /v1/leagues/{leagueId}` and `DELETE /v1/leagues/{leagueId}/membership` (docs/server/api.md).
 */
fun Route.leagueRoutes(leagues: Leagues) {
    authenticate(SIGNED_IN) {
        post(ApiPaths.LEAGUES) {
            val request = call.receive<CreateLeagueRequest>()
            call.respondOutcome(withContext(Dispatchers.IO) { leagues.create(player(), request) }, HttpStatusCode.Created)
        }

        get(ApiPaths.LEAGUES) {
            call.respond(LeagueListResponse(withContext(Dispatchers.IO) { leagues.mine(player()) }))
        }

        post(ApiPaths.JOIN_LEAGUE) {
            val request = call.receive<JoinLeagueRequest>()
            call.respondOutcome(withContext(Dispatchers.IO) { leagues.join(player(), request.inviteCode) }, HttpStatusCode.OK)
        }

        post(ApiPaths.AUTO_JOIN_LEAGUE) {
            call.respondOutcome(withContext(Dispatchers.IO) { leagues.autoJoin(player()) }, HttpStatusCode.OK)
        }

        get(ApiPaths.league("{$LEAGUE_ID}")) {
            val leagueId = call.parameters[LEAGUE_ID].orEmpty()
            val detail = withContext(Dispatchers.IO) { leagues.detail(player(), leagueId) }
            if (detail == null) call.respondNotFound() else call.respond(detail)
        }

        delete(ApiPaths.membership("{$LEAGUE_ID}")) {
            val leagueId = call.parameters[LEAGUE_ID].orEmpty()
            val left = withContext(Dispatchers.IO) { leagues.leave(player(), leagueId) }
            if (left) call.respond(HttpStatusCode.NoContent) else call.respondNotFound()
        }
    }
}

private suspend fun ApplicationCall.respondOutcome(outcome: LeagueOutcome, success: HttpStatusCode) {
    when (outcome) {
        is LeagueOutcome.InLeague -> respond(success, outcome.league)
        is LeagueOutcome.Refused -> {
            val (status, code) = refusal(outcome.reason)
            refuse(status, code)
        }
    }
}

private fun refusal(reason: LeagueRefusal): Pair<HttpStatusCode, ApiErrorCode> = when (reason) {
    LeagueRefusal.NAME_INVALID -> HttpStatusCode.UnprocessableEntity to ApiErrorCode.LEAGUE_NAME_INVALID
    LeagueRefusal.TIME_ZONE_UNKNOWN -> HttpStatusCode.UnprocessableEntity to ApiErrorCode.TIME_ZONE_UNKNOWN
    LeagueRefusal.START_NOT_A_MONDAY -> HttpStatusCode.UnprocessableEntity to ApiErrorCode.SEASON_START_NOT_A_MONDAY
    LeagueRefusal.START_TOO_SOON -> HttpStatusCode.UnprocessableEntity to ApiErrorCode.SEASON_START_TOO_SOON
    LeagueRefusal.NO_DISPLAY_NAME -> HttpStatusCode.UnprocessableEntity to ApiErrorCode.DISPLAY_NAME_INVALID
    LeagueRefusal.ALREADY_IN_A_LEAGUE -> HttpStatusCode.Conflict to ApiErrorCode.ALREADY_IN_A_LEAGUE
    LeagueRefusal.INVITE_CODE_UNKNOWN -> HttpStatusCode.NotFound to ApiErrorCode.INVITE_CODE_UNKNOWN
    LeagueRefusal.ALREADY_A_MEMBER -> HttpStatusCode.Conflict to ApiErrorCode.ALREADY_A_MEMBER
    LeagueRefusal.JOINING_CLOSED -> HttpStatusCode.Conflict to ApiErrorCode.JOINING_CLOSED
    LeagueRefusal.FULL -> HttpStatusCode.Conflict to ApiErrorCode.LEAGUE_FULL
    LeagueRefusal.NO_SUCH_PLAYER -> HttpStatusCode.Unauthorized to ApiErrorCode.UNAUTHORIZED
}

private suspend fun ApplicationCall.respondNotFound() {
    refuse(HttpStatusCode.NotFound, ApiErrorCode.LEAGUE_NOT_FOUND)
}
