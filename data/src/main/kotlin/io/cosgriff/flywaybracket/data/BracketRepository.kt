package io.cosgriff.flywaybracket.data

import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.BracketResponse
import io.cosgriff.flywaybracket.api.LeagueDetail
import io.cosgriff.flywaybracket.api.MatchupSlot
import io.cosgriff.flywaybracket.api.PicksResponse
import io.cosgriff.flywaybracket.api.SubmitPicksRequest
import io.cosgriff.flywaybracket.data.db.BracketDao
import io.cosgriff.flywaybracket.data.db.BracketEntity
import io.cosgriff.flywaybracket.data.db.BracketPickEntity
import io.cosgriff.flywaybracket.data.db.BracketTeamEntity
import io.cosgriff.flywaybracket.data.db.MatchupResultEntity
import io.cosgriff.flywaybracket.data.db.OutingDao
import io.cosgriff.flywaybracket.data.db.StoredBracket
import io.cosgriff.flywaybracket.data.server.Credentials
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.data.server.ServerResult
import io.cosgriff.flywaybracket.domain.bracket.BracketPicks
import io.cosgriff.flywaybracket.domain.bracket.BracketView
import io.cosgriff.flywaybracket.domain.bracket.DecidedMatchup
import io.cosgriff.flywaybracket.domain.bracket.Decision
import io.cosgriff.flywaybracket.domain.bracket.DrawPosition
import io.cosgriff.flywaybracket.domain.bracket.Field
import io.cosgriff.flywaybracket.domain.bracket.PickScore
import io.cosgriff.flywaybracket.domain.bracket.PicksCheck
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendarResult
import io.cosgriff.flywaybracket.domain.bracket.Tally
import io.cosgriff.flywaybracket.domain.bracket.Team
import java.time.Clock
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Why the phone's picks changed without the player picking, so the bracket screen can say so (#41). */
enum class PicksNotice {
    /**
     * Picks locked before the phone could send the player's latest: the server's lock wins, so the picks
     * the server held at the lock are the player's, and the phone's later changes are gone.
     */
    LOCKED_BEFORE_SENT,

    /**
     * The preview was seeded again and some matchups' birds changed (#35): the picks of those matchups, and
     * every later pick that depended on them, were dropped.
     */
    BRACKET_CHANGED,
}

/**
 * A league's bracket on the phone (#41): the view the bracket screens draw, and what they need to say about
 * the picks.
 *
 * @property preview whether the field could still change when it was last fetched.
 * @property timeZone the league's zone: every date of [calendar] is a date there.
 * @property lockedByServer whether the server has said picks are locked.
 * @property picksUnsent whether the phone holds picks the server has not taken yet; they go with the next
 *   [BracketRepository.refresh] or pick.
 * @property notice why the picks last changed without the player; null when there is nothing to tell.
 */
data class LeagueBracket(
    val leagueId: String,
    val view: BracketView,
    val preview: Boolean,
    val calendar: SeasonCalendar,
    val timeZone: ZoneId,
    val lockedByServer: Boolean,
    val picksUnsent: Boolean,
    val notice: PicksNotice?,
) {
    /**
     * Whether picks are locked at [now]: from midnight at the start of the season's first Monday in the
     * league's zone, or from when the server said so, whichever is first.
     */
    fun picksLocked(now: Instant): Boolean = lockedByServer || calendar.arePicksLocked(now.atZone(timeZone).toLocalDateTime())

    /** The round played at [now], in the league's zone; null before the season and after the final. */
    fun roundOn(now: Instant): Round? = calendar.roundOn(now.atZone(timeZone).toLocalDate())
}

/** What picking a bird gave. */
sealed interface PickResult {
    /**
     * The picks are saved on the phone. [sent] is how sending them went; picks not sent go with the next
     * [BracketRepository.refresh] or pick.
     */
    data class Saved(val sent: ServerResult<Unit>) : PickResult

    /** Picks are locked: nothing changed. */
    data object Locked : PickResult

    /** The pick does not fit the player's other picks ([BracketPicks.check]): nothing changed. */
    data class DoesNotFit(val check: PicksCheck) : PickResult

    /** The phone has no bracket for the league yet: [BracketRepository.refresh] first. */
    data object NoBracket : PickResult
}

/**
 * A league's bracket, the player's picks and the results (#41), kept in Room and fetched from the league
 * server (`docs/server/api.md`). Screens read the [bracket] flow; [refresh] brings it up to date.
 *
 * Picks are saved on the phone at once and then sent. Picks the server has not taken stay marked unsent and
 * go with the next [refresh] or pick. The server is the truth: after each exchange the phone keeps the picks
 * the server holds, and a `PICKS_LOCKED` answer wins over picks the phone had not sent
 * ([PicksNotice.LOCKED_BEFORE_SENT]). The only thing this sends is the picks, as ADR 0001 lists.
 *
 * Made by `ServerModule`.
 */
class BracketRepository(
    private val server: ServerClient,
    private val accounts: AccountRepository,
    private val dao: BracketDao,
    private val outings: OutingDao,
    private val clock: Clock,
) {
    // Held while the phone's copy is read, changed and saved; never across a call to the server.
    private val writes = Mutex()

    // One exchange with the server at a time, so two sends never cross.
    private val exchanges = Mutex()

    /** The league's bracket as last fetched, and again after each change; null until the first fetch. */
    fun bracket(leagueId: String): Flow<LeagueBracket?> =
        dao.observe(leagueId).map { it?.toLeagueBracket() }.distinctUntilChanged()

    /**
     * The player's picks still alive in the round being played that they have not logged in it
     * ([BracketView.picksNotYetSeen]), for Home and the Summary screen (#26). Empty before the season, after
     * the final, and with no bracket. A sighting counts for the date it was first logged on, in the phone's
     * zone. The round is the one being played when the bracket or the sightings last changed.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun picksNotYetSeen(leagueId: String): Flow<List<Team>> = bracket(leagueId).flatMapLatest { bracket ->
        val round = bracket?.roundOn(clock.instant()) ?: return@flatMapLatest flowOf(emptyList())
        val dates = bracket.calendar.roundDates(round)
        val from = dates.start.atStartOfDay(clock.zone).toInstant().toEpochMilli()
        val until = dates.endInclusive.plusDays(1).atStartOfDay(clock.zone).toInstant().toEpochMilli()
        outings.observeBirdsSeenBetween(from, until).map { logged -> bracket.view.picksNotYetSeen(round, logged.toSet()) }
    }.distinctUntilChanged()

    /**
     * Picks [bird] to win [at], or clears the pick there when [bird] is null. Later picks that depended on the
     * bird picked there before are cleared with it ([BracketPicks.withPick]). The picks are checked and saved
     * on the phone, then sent.
     */
    suspend fun pick(leagueId: String, at: DrawPosition, bird: String?): PickResult {
        writes.withLock {
            val stored = dao.get(leagueId) ?: return PickResult.NoBracket
            val bracket = stored.toLeagueBracket()
            if (bracket.picksLocked(clock.instant())) return PickResult.Locked
            val picks = stored.picksByPosition()
            val changed = BracketPicks.withPick(picks, at, bird)
            if (changed != picks) {
                val check = BracketPicks.check(bracket.view.teams, changed)
                if (check != PicksCheck.Consistent) return PickResult.DoesNotFit(check)
                dao.savePicks(stored.bracket.copy(picksUnsent = true), changed.toEntities(leagueId))
            }
        }
        return PickResult.Saved(sendPicks(leagueId))
    }

    /** Sends the picks the phone holds, if the server has not taken them yet. */
    suspend fun sendPicks(leagueId: String): ServerResult<Unit> = exchanges.withLock {
        val credentials = accounts.credentials() ?: return NO_ACCOUNT
        sendLocked(credentials, leagueId)
    }

    /**
     * Brings the league's bracket up to date: its dates, its birds and matchups, the player's picks, and the
     * results of every decided round not yet on the phone. Picks not yet sent are sent first; with none, the
     * server's picks replace the phone's. Nothing is changed by a fetch that fails.
     */
    suspend fun refresh(leagueId: String): ServerResult<Unit> = exchanges.withLock {
        val credentials = accounts.credentials() ?: return NO_ACCOUNT
        fetchLocked(credentials, leagueId, send = true)
    }

    /** Clears [LeagueBracket.notice], once the screen has said it. */
    suspend fun dismissNotice(leagueId: String) {
        writes.withLock {
            val stored = dao.get(leagueId) ?: return
            if (stored.bracket.picksNotice != null) dao.upsert(stored.bracket.copy(picksNotice = null))
        }
    }

    /** Only with [exchanges] held. */
    private suspend fun fetchLocked(credentials: Credentials, leagueId: String, send: Boolean): ServerResult<Unit> {
        val league = server.league(credentials, leagueId)
        if (league !is ServerResult.Ok) return league as ServerResult.Failed
        val bracket = server.bracket(credentials, leagueId)
        if (bracket !is ServerResult.Ok) return bracket as ServerResult.Failed
        saveBracket(leagueId, league.value, bracket.value)?.let { return it }

        val unsent = dao.get(leagueId)?.bracket?.picksUnsent == true
        if (send && unsent) {
            val sent = sendLocked(credentials, leagueId)
            if (sent is ServerResult.Failed) return sent
        } else {
            val picks = server.picks(credentials, leagueId)
            if (picks !is ServerResult.Ok) return picks as ServerResult.Failed
            takeServerPicks(leagueId, picks.value)
        }
        return fetchResults(credentials, leagueId, bracket.value)
    }

    /** Only with [exchanges] held. */
    private suspend fun sendLocked(credentials: Credentials, leagueId: String): ServerResult<Unit> {
        val stored = dao.get(leagueId) ?: return OK
        if (!stored.bracket.picksUnsent) return OK
        val sent = stored.picksByPosition()
        val answer = server.submitPicks(credentials, leagueId, SubmitPicksRequest(sent.mapKeys { it.key.toSlot() }))
        val problem = when (answer) {
            is ServerResult.Ok -> {
                takeAnswer(leagueId, sent, answer.value)
                return OK
            }
            is ServerResult.Failed -> answer.problem
        }
        if (problem == ServerProblem.Refused(ApiErrorCode.PICKS_LOCKED)) {
            // The lock came before the picks: the server's picks are the player's.
            markPicks(leagueId) { it.copy(picksLocked = true, picksUnsent = false, picksNotice = PicksNotice.LOCKED_BEFORE_SENT.name) }
            val picks = server.picks(credentials, leagueId)
            if (picks is ServerResult.Ok) takeServerPicks(leagueId, picks.value)
        } else if (problem in BRACKET_MOVED) {
            // The picks fit the bracket the phone has, not the one the server has: the preview was seeded again.
            // The server dropped what no longer fits; the phone takes its bracket and picks.
            markPicks(leagueId) { it.copy(picksUnsent = false, picksNotice = PicksNotice.BRACKET_CHANGED.name) }
            fetchLocked(credentials, leagueId, send = false)
        }
        return answer
    }

    /**
     * Saves the league's dates and the bracket as fetched. When the birds changed (a preview seeded again),
     * picks of the matchups whose birds changed, and later picks that depended on them, are dropped as the
     * server drops them ([BracketPicks.withoutChanged]). Returns a failure when the answer is not one the
     * phone can keep, else null.
     */
    private suspend fun saveBracket(leagueId: String, league: LeagueDetail, bracket: BracketResponse): ServerResult.Failed? {
        val teams = try {
            ZoneId.of(league.timeZone)
            require(SeasonCalendar.of(league.seasonStart) is SeasonCalendarResult.Valid)
            bracket.teams.map { Team(it.commonName, it.quarter.name, it.seed) }.also { require(it.size == Field.SIZE) }
        } catch (_: IllegalArgumentException) {
            return UNREADABLE
        } catch (_: DateTimeException) {
            return UNREADABLE
        }
        writes.withLock {
            val old = dao.get(leagueId)
            val oldTeams = old?.teams?.map { it.toDomain() }
            var picks: Map<DrawPosition, String>? = null
            var notice = old?.bracket?.picksNotice
            if (old != null && oldTeams != null && oldTeams.toSet() != teams.toSet()) {
                val had = old.picksByPosition()
                val kept = BracketPicks.withoutChanged(had, Field.changedMatchups(oldTeams, teams))
                if (kept != had) {
                    picks = kept
                    notice = PicksNotice.BRACKET_CHANGED.name
                }
            }
            val row = BracketEntity(
                leagueId = leagueId,
                preview = bracket.preview,
                seasonStart = league.seasonStart.toString(),
                timeZone = league.timeZone,
                picksLocked = old?.bracket?.picksLocked ?: false,
                picksUnsent = old?.bracket?.picksUnsent ?: false,
                picksNotice = notice,
            )
            dao.saveBracket(row, teams.map { BracketTeamEntity(leagueId, it.commonName, it.quarter, it.seed) }, picks?.toEntities(leagueId))
        }
        return null
    }

    /**
     * The server stored [answer] for the picks [sent]. They replace the phone's, unless the player picked again
     * while they were on their way: those are left unsent, for the next send.
     */
    private suspend fun takeAnswer(leagueId: String, sent: Map<DrawPosition, String>, answer: PicksResponse) {
        writes.withLock {
            val stored = dao.get(leagueId) ?: return
            if (stored.picksByPosition() == sent) {
                dao.savePicks(stored.bracket.copy(picksUnsent = false, picksLocked = answer.locked), answer.picks.toEntities(leagueId))
            } else {
                dao.upsert(stored.bracket.copy(picksLocked = answer.locked))
            }
        }
    }

    /** The server's picks replace the phone's, unless the phone has picks it has not sent. */
    private suspend fun takeServerPicks(leagueId: String, answer: PicksResponse) {
        writes.withLock {
            val stored = dao.get(leagueId) ?: return
            if (stored.bracket.picksUnsent) {
                dao.upsert(stored.bracket.copy(picksLocked = answer.locked))
            } else {
                dao.savePicks(stored.bracket.copy(picksLocked = answer.locked), answer.picks.toEntities(leagueId))
            }
        }
    }

    private suspend fun markPicks(leagueId: String, change: (BracketEntity) -> BracketEntity) {
        writes.withLock {
            val stored = dao.get(leagueId) ?: return
            dao.upsert(change(stored.bracket))
        }
    }

    /** Fetches the results of each round the bracket shows decided whose results the phone does not have. */
    private suspend fun fetchResults(credentials: Credentials, leagueId: String, bracket: BracketResponse): ServerResult<Unit> {
        val decided = Round.entries.filter { round ->
            val matchups = bracket.matchups.filter { it.slot.round == round }
            matchups.size == MatchupSlot.matchupsIn(round) && matchups.all { it.winner != null }
        }
        val held = dao.get(leagueId)?.results.orEmpty().groupBy { MatchupSlot(it.slot).round }
        for (round in decided) {
            if (held[round].orEmpty().size == MatchupSlot.matchupsIn(round)) continue
            val results = server.roundResults(credentials, leagueId, round)
            if (results !is ServerResult.Ok) return results as ServerResult.Failed
            val rows = try {
                results.value.matchups.map { result ->
                    // Built as :domain values first, so a figure that cannot be is refused here.
                    DecidedMatchup(
                        winner = result.winner,
                        upper = Tally(result.upper.eligible, result.upper.logged),
                        lower = Tally(result.lower.eligible, result.lower.logged),
                        reason = result.reason,
                        score = PickScore(result.roundPoints, result.upsetBonus),
                    ).toEntity(leagueId, result.slot)
                }
            } catch (_: IllegalArgumentException) {
                return UNREADABLE
            }
            writes.withLock { if (dao.get(leagueId) != null) dao.upsertResults(rows) }
        }
        return OK
    }

    private companion object {
        val OK = ServerResult.Ok(Unit)

        /** With no account there is no league: the server would answer this. */
        val NO_ACCOUNT = ServerResult.Failed(ServerProblem.Refused(ApiErrorCode.UNAUTHORIZED))

        /** An answer the phone cannot keep: a general failure. */
        val UNREADABLE = ServerResult.Failed(ServerProblem.Refused(code = null))

        /** Refusals of picks that fit the phone's bracket, which mean the server's bracket has changed. */
        val BRACKET_MOVED = setOf(
            ServerProblem.Refused(ApiErrorCode.PICKS_INCOMPLETE),
            ServerProblem.Refused(ApiErrorCode.BIRD_UNKNOWN),
        )
    }
}

private fun DrawPosition.toSlot(): MatchupSlot = MatchupSlot.of(round, position)

private fun MatchupSlot.toPosition(): DrawPosition = DrawPosition(round, position)

private fun Map<DrawPosition, String>.toEntities(leagueId: String): List<BracketPickEntity> =
    map { (at, bird) -> BracketPickEntity(leagueId, at.toSlot().index, bird) }

@JvmName("slotPicksToEntities")
private fun Map<MatchupSlot, String>.toEntities(leagueId: String): List<BracketPickEntity> =
    map { (slot, bird) -> BracketPickEntity(leagueId, slot.index, bird) }

private fun StoredBracket.picksByPosition(): Map<DrawPosition, String> =
    picks.associate { MatchupSlot(it.slot).toPosition() to it.bird }

private fun BracketTeamEntity.toDomain() = Team(commonName, quarter, seed)

private fun DecidedMatchup.toEntity(leagueId: String, slot: MatchupSlot) = MatchupResultEntity(
    leagueId = leagueId,
    slot = slot.index,
    winner = winner,
    upperEligible = upper.eligible,
    upperLogged = upper.logged,
    lowerEligible = lower.eligible,
    lowerLogged = lower.logged,
    reason = reason.name,
    roundPoints = score.roundPoints,
    upsetBonus = score.upsetBonus,
)

private fun StoredBracket.toLeagueBracket(): LeagueBracket {
    val teams = teams.map { it.toDomain() }.sortedWith(compareBy({ Field.quarters.indexOf(it.quarter) }, { it.seed }))
    val results = results.associate { row ->
        MatchupSlot(row.slot).toPosition() to DecidedMatchup(
            winner = row.winner,
            upper = Tally(row.upperEligible, row.upperLogged),
            lower = Tally(row.lowerEligible, row.lowerLogged),
            reason = Decision.Reason.valueOf(row.reason),
            score = PickScore(row.roundPoints, row.upsetBonus),
        )
    }
    return LeagueBracket(
        leagueId = bracket.leagueId,
        view = BracketView.of(teams, picksByPosition(), results),
        preview = bracket.preview,
        // Checked before it was saved.
        calendar = (SeasonCalendar.of(LocalDate.parse(bracket.seasonStart)) as SeasonCalendarResult.Valid).calendar,
        timeZone = ZoneId.of(bracket.timeZone),
        lockedByServer = bracket.picksLocked,
        picksUnsent = bracket.picksUnsent,
        notice = bracket.picksNotice?.let { name -> PicksNotice.entries.firstOrNull { it.name == name } },
    )
}
