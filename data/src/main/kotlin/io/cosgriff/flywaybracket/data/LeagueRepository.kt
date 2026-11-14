package io.cosgriff.flywaybracket.data

import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.CreateLeagueRequest
import io.cosgriff.flywaybracket.api.LeagueDetail
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.LeagueSummary
import io.cosgriff.flywaybracket.data.db.LeagueDao
import io.cosgriff.flywaybracket.data.db.LeagueEntity
import io.cosgriff.flywaybracket.data.db.LeagueMemberEntity
import io.cosgriff.flywaybracket.data.db.StoredLeague
import io.cosgriff.flywaybracket.data.server.Credentials
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.data.server.ServerResult
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendarResult
import io.cosgriff.flywaybracket.domain.league.CloseReason
import io.cosgriff.flywaybracket.domain.league.InviteCode
import io.cosgriff.flywaybracket.domain.league.LeagueName
import io.cosgriff.flywaybracket.domain.league.LeagueNameCheck
import io.cosgriff.flywaybracket.domain.league.LeaguePhase
import io.cosgriff.flywaybracket.domain.league.LeagueTimeZone
import io.cosgriff.flywaybracket.domain.league.SeasonStart
import io.cosgriff.flywaybracket.domain.league.SeasonStartCheck
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One row of a league's table, as the server ranked it. No county, no account id (ADR 0001). */
data class LeagueMember(
    val displayName: String,
    val points: Int,
    val rightPicks: Int,
    val rank: Int,
    val you: Boolean,
    val memberKey: String,
)

/**
 * The player's league as the phone last fetched it (#40).
 *
 * @property inviteCode what another player types to join; null when the server did not give it.
 * @property closedBecause why a [LeaguePhase.CLOSED] league never started; null in every other phase.
 * @property timeZone the league's zone, in which every date of [calendar] is; null, with [calendar], when only
 *   the league's summary could be fetched.
 * @property members the league's table, best rank first; empty when only the summary could be fetched.
 * @property fetchedAt when the phone last read the league from the server.
 */
data class League(
    val id: String,
    val name: String,
    val inviteCode: String?,
    val players: Int,
    val capacity: Int,
    val phase: LeaguePhase,
    val closedBecause: CloseReason?,
    val timeZone: ZoneId?,
    val calendar: SeasonCalendar?,
    val members: List<LeagueMember>,
    val fetchedAt: Instant,
) {
    /** The player's own row; null when the table was not fetched. */
    val you: LeagueMember? get() = members.firstOrNull { it.you }
}

/**
 * The player's league (#40), fetched from the league server (`docs/server/api.md`) and kept in Room, so the
 * League tab and Home open with it at once and show it offline, with when it was fetched. Creating, joining,
 * finding and leaving a league go through here.
 *
 * With no account nothing is sent: every call answers `UNAUTHORIZED`, as the server would, and [league] is
 * null (ADR 0001). What is sent is what ADR 0001 lists: league membership, and for a new league its name,
 * whether it is open, its time zone and its start date. A name, a start or an invite code that the shared
 * rules in `:domain` refuse is not sent at all.
 *
 * Made by `ServerModule`.
 */
class LeagueRepository(
    private val server: ServerClient,
    private val accounts: AccountRepository,
    private val dao: LeagueDao,
    private val clock: Clock,
) {
    // One exchange with the server at a time, so a refresh never saves over a join that came after it.
    private val exchanges = Mutex()

    /** The league as last fetched, and again after each change; null with no league, or no account. */
    fun league(): Flow<League?> =
        combine(dao.observe(), accounts.hasAccount()) { stored, hasAccount -> if (hasAccount) stored?.toLeague() else null }
            .distinctUntilChanged()

    /**
     * Reads the player's league from the server (`GET /v1/account`, then the league) and keeps it in place of
     * the phone's. With no league, the phone's is deleted. A fetch that fails changes nothing.
     */
    suspend fun refresh(): ServerResult<Unit> = exchanges.withLock {
        val credentials = accounts.credentials() ?: return noAccount()
        when (val profile = server.profile(credentials)) {
            is ServerResult.Failed -> profile
            is ServerResult.Ok -> {
                val summary = profile.value.league
                if (summary == null) {
                    dao.clear()
                    OK
                } else {
                    fetchLocked(credentials, summary)
                }
            }
        }
    }

    /**
     * Creates a league with the player as its first member (`POST /v1/leagues`) and keeps it. The rules the
     * server applies are checked first, with the same functions: a [name] [LeagueName] refuses, a zone the
     * server would not know, or a [seasonStart] [SeasonStart] refuses on today's date in [timeZone], is
     * answered with the server's code and not sent.
     *
     * @return the league as kept, with its invite code. A league whose table could not be read after it was
     *   made is kept from its summary: it was made all the same.
     */
    suspend fun create(name: String, open: Boolean, timeZone: ZoneId, seasonStart: LocalDate): ServerResult<League> {
        if (LeagueName.check(name) != LeagueNameCheck.VALID) return refused(ApiErrorCode.LEAGUE_NAME_INVALID)
        val zone = LeagueTimeZone.of(timeZone.id) ?: return refused(ApiErrorCode.TIME_ZONE_UNKNOWN)
        when (SeasonStart.check(seasonStart, LocalDate.now(clock.withZone(zone)))) {
            SeasonStartCheck.NOT_A_MONDAY -> return refused(ApiErrorCode.SEASON_START_NOT_A_MONDAY)
            SeasonStartCheck.TOO_SOON -> return refused(ApiErrorCode.SEASON_START_TOO_SOON)
            SeasonStartCheck.VALID -> Unit
        }
        val request = CreateLeagueRequest(name = name.trim(), open = open, timeZone = zone.id, seasonStart = seasonStart)
        return enter { credentials -> server.createLeague(credentials, request) }
    }

    /**
     * Joins the league whose invite code the player typed (`POST /v1/leagues/join`) and keeps it. A code that
     * cannot be one ([InviteCode.normalize]) is answered `INVITE_CODE_UNKNOWN` and not sent.
     */
    suspend fun join(typedCode: String): ServerResult<League> {
        val code = InviteCode.normalize(typedCode) ?: return refused(ApiErrorCode.INVITE_CODE_UNKNOWN)
        return enter { credentials -> server.joinLeague(credentials, code) }
    }

    /** Asks the server to place the player in an open league with room (`POST /v1/leagues/auto-join`). Sends nothing else. */
    suspend fun findLeague(): ServerResult<League> = enter { credentials -> server.autoJoinLeague(credentials) }

    /**
     * Leaves the league the phone holds (`DELETE …/membership`), then reads the player's league again: they
     * may still have a finished or closed one. A league the server no longer has them in counts as left.
     */
    suspend fun leave(): ServerResult<Unit> = exchanges.withLock {
        val credentials = accounts.credentials() ?: return noAccount()
        val league = dao.get()?.league ?: return OK
        val left = server.leaveLeague(credentials, league.leagueId)
        val gone = left is ServerResult.Ok || (left is ServerResult.Failed && left.problem == ServerProblem.Refused(ApiErrorCode.LEAGUE_NOT_FOUND))
        if (!gone) return left
        dao.clear()
        // The player has left either way; a failure here only leaves the tab without their older league.
        (server.profile(credentials) as? ServerResult.Ok)?.value?.league?.let { fetchLocked(credentials, it) }
        OK
    }

    /** Sends [call], which puts the player in a league, then keeps that league with its table. */
    private suspend fun enter(call: suspend (Credentials) -> ServerResult<LeagueSummary>): ServerResult<League> = exchanges.withLock {
        val credentials = accounts.credentials() ?: return noAccount()
        when (val entered = call(credentials)) {
            is ServerResult.Failed -> entered
            is ServerResult.Ok -> {
                fetchLocked(credentials, entered.value)
                dao.get()?.toLeague()?.let { ServerResult.Ok(it) } ?: UNREADABLE
            }
        }
    }

    /**
     * Fetches the league [summary] names with its dates and table, and keeps it. When that fails, a league the
     * phone does not hold yet is kept from [summary] alone; the one it holds stays as it was.
     *
     * Only with [exchanges] held.
     */
    private suspend fun fetchLocked(credentials: Credentials, summary: LeagueSummary): ServerResult<Unit> {
        val detail = server.league(credentials, summary.id)
        val fetchedAt = clock.millis()
        if (detail is ServerResult.Ok) {
            val read = detail.value.toEntities(fetchedAt) ?: return UNREADABLE
            dao.replace(read.first, read.second)
            return OK
        }
        if (dao.get()?.league?.leagueId != summary.id) summary.toEntity(fetchedAt, timeZone = null, seasonStart = null)?.let { dao.replace(it, emptyList()) }
        return detail as ServerResult.Failed
    }

    /** With no account there is no league to keep: the phone's is deleted, and the server would answer this. */
    private suspend fun noAccount(): ServerResult.Failed {
        dao.clear()
        return ServerResult.Failed(ServerProblem.Refused(ApiErrorCode.UNAUTHORIZED))
    }

    private companion object {
        val OK = ServerResult.Ok(Unit)

        /** An answer the phone cannot keep: a general failure. */
        val UNREADABLE = ServerResult.Failed(ServerProblem.Refused(code = null))

        fun refused(code: ApiErrorCode) = ServerResult.Failed(ServerProblem.Refused(code))
    }
}

private fun LeagueState.toPhase(): LeaguePhase = when (this) {
    LeagueState.BEFORE_SEASON -> LeaguePhase.FORMING
    LeagueState.IN_SEASON -> LeaguePhase.RUNNING
    LeagueState.FINISHED -> LeaguePhase.FINISHED
    LeagueState.CLOSED -> LeaguePhase.CLOSED
}

/** The rows for [LeagueSummary]; null when it is not one the phone can keep. */
private fun LeagueSummary.toEntity(fetchedAt: Long, timeZone: String?, seasonStart: LocalDate?): LeagueEntity? {
    if (players < 0 || capacity < 0) return null
    return LeagueEntity(
        leagueId = id,
        name = name,
        inviteCode = inviteCode,
        players = players,
        capacity = capacity,
        phase = state.toPhase().name,
        closedBecause = closedBecause?.name,
        timeZone = timeZone,
        seasonStart = seasonStart?.toString(),
        fetchedAt = fetchedAt,
    )
}

/** The rows for the league and its table; null when the zone or the start date is not one the phone can use. */
private fun LeagueDetail.toEntities(fetchedAt: Long): Pair<LeagueEntity, List<LeagueMemberEntity>>? {
    if (LeagueTimeZone.of(timeZone) == null || SeasonCalendar.of(seasonStart) !is SeasonCalendarResult.Valid) return null
    val row = league.toEntity(fetchedAt, timeZone, seasonStart) ?: return null
    val members = members.mapIndexed { position, member ->
        LeagueMemberEntity(
            leagueId = league.id,
            position = position,
            memberKey = member.memberKey,
            displayName = member.displayName,
            points = member.points,
            rightPicks = member.rightPicks,
            rank = member.rank,
            you = member.you,
        )
    }
    return row to members
}

private fun StoredLeague.toLeague(): League = League(
    id = league.leagueId,
    name = league.name,
    inviteCode = league.inviteCode,
    players = league.players,
    capacity = league.capacity,
    phase = LeaguePhase.valueOf(league.phase),
    closedBecause = league.closedBecause?.let(CloseReason::valueOf),
    // Both checked before they were saved.
    timeZone = league.timeZone?.let(ZoneId::of),
    calendar = league.seasonStart?.let { (SeasonCalendar.of(LocalDate.parse(it)) as SeasonCalendarResult.Valid).calendar },
    members = members.sortedBy { it.position }.map { LeagueMember(it.displayName, it.points, it.rightPicks, it.rank, it.you, it.memberKey) },
    fetchedAt = Instant.ofEpochMilli(league.fetchedAt),
)
