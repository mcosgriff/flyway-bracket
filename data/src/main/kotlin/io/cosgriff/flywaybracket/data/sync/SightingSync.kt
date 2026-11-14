package io.cosgriff.flywaybracket.data.sync

import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.api.SightingUpload
import io.cosgriff.flywaybracket.api.UploadSightingsRequest
import io.cosgriff.flywaybracket.data.AccountRepository
import io.cosgriff.flywaybracket.data.BracketRepository
import io.cosgriff.flywaybracket.data.db.SyncAction
import io.cosgriff.flywaybracket.data.db.SyncDao
import io.cosgriff.flywaybracket.data.db.SyncQueueEntity
import io.cosgriff.flywaybracket.data.db.SyncStateEntity
import io.cosgriff.flywaybracket.data.server.Credentials
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.data.server.ServerResult
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendar
import io.cosgriff.flywaybracket.domain.bracket.SeasonCalendarResult
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Whether a finished outing's sightings have reached the league server (#43). */
enum class OutingSendState {
    /** The outing has no sightings: there is nothing to send. */
    NOTHING_TO_SEND,

    /** The server has every one of them. */
    SENT,

    /** Some are waiting to be sent, and go when the phone is online. */
    WAITING,

    /**
     * Some stay on the phone: there is no account or no league, they are dated outside the league's season,
     * or the server refused them.
     */
    ON_PHONE,
}

/**
 * The queue as the screens tell it (#43).
 *
 * @property due how many rows would be sent now: deletes, and uploads dated inside the league's season; none
 *   without an account.
 * @property waitingSince since when the longest-waiting of them has waited; null when none is due. An upload
 *   waits from when it was queued or from when the player's league was set, whichever is later.
 */
data class SyncQueueState(val due: Int, val waitingSince: Instant?)

/** What a run of [SightingSync.run] gave. */
data class SyncRun(
    /** Something could not be sent or fetched and is worth trying again, after a backoff. */
    val retry: Boolean,
    /**
     * Whether the player's league is in season, so results are worth fetching every hour; null when the run
     * did not find out.
     */
    val seasonRunning: Boolean?,
)

/**
 * Keeps the phone and the league server in step (#43): sends the queued sightings and deletes, and refreshes
 * the player's league, bracket and results. `SyncWorker` runs it in the background.
 *
 * Nothing is sent without an account (#38), which exists only once the player has agreed (ADR 0001). An
 * upload carries a sighting's id, bird, county and date, nothing else, and only sightings dated inside the
 * player's league's season are sent ([SeasonCalendar.isSentForSeason]): one logged before the player joined
 * is sent once they have, and one outside stays on the phone. The season is the one of the league the player's
 * profile last named, from the bracket [BracketRepository] keeps.
 *
 * The server keeps one sighting per id, so a batch sent again after a lost answer counts nothing twice.
 *
 * Made by `ServerModule`.
 */
class SightingSync(
    private val server: ServerClient,
    private val accounts: AccountRepository,
    private val brackets: BracketRepository,
    private val dao: SyncDao,
    private val clock: Clock,
) {
    // One run at a time in this process.
    private val runs = Mutex()

    /** The queue as Home tells it, and again after each change. */
    fun queueState(): Flow<SyncQueueState> =
        combine(accounts.hasAccount(), dao.observeQueue(), season(), dao.observeLeagueSince()) { has, queue, season, since ->
            val due = if (has) queue.filter { it.isDue(season) } else emptyList()
            val waiting = due.minOfOrNull { if (it.action == SyncAction.UPLOAD.name) maxOf(it.queuedAt, since ?: 0) else it.queuedAt }
            SyncQueueState(due.size, waiting?.let(Instant::ofEpochMilli))
        }.distinctUntilChanged()

    /** Whether a finished outing's sightings have been sent, and again after each change. */
    fun outing(outingId: Long): Flow<OutingSendState> = combine(
        accounts.hasAccount(),
        dao.observeSightingCount(outingId),
        dao.observeOutingQueue(outingId),
        season(),
    ) { has, sightings, queued, season ->
        when {
            sightings == 0 -> OutingSendState.NOTHING_TO_SEND
            queued.isEmpty() -> OutingSendState.SENT
            has && queued.any { it.isDue(season) } -> OutingSendState.WAITING
            else -> OutingSendState.ON_PHONE
        }
    }.distinctUntilChanged()

    /**
     * Refreshes the league when [refresh] says so, then sends every upload that is due, in batches of at most
     * `UploadSightingsRequest.MAX_SIGHTINGS`, and every delete. With no account it does nothing.
     */
    suspend fun run(refresh: Boolean): SyncRun = runs.withLock {
        val credentials = accounts.credentials() ?: return SyncRun(retry = false, seasonRunning = false)
        var seasonRunning: Boolean? = null
        var retry = false
        if (refresh) {
            when (val profile = accounts.profile()) {
                is ServerResult.Failed -> {
                    if (profile.problem == UNAUTHORIZED) return SyncRun(retry = false, seasonRunning = false)
                    retry = true
                }
                is ServerResult.Ok -> {
                    // A league that closed never started: it has no season.
                    val league = profile.value.league?.takeIf { it.state != LeagueState.CLOSED }
                    setLeague(league?.id)
                    seasonRunning = league?.state == LeagueState.IN_SEASON
                    if (league != null) {
                        val refreshed = brackets.refresh(league.id)
                        if (refreshed is ServerResult.Failed && refreshed.problem != BRACKET_NOT_READY) retry = true
                    }
                }
            }
        }
        when (sendUploads(credentials)) {
            Sent.DONE -> Unit
            Sent.RETRY -> retry = true
            Sent.NO_ACCOUNT -> return SyncRun(retry = false, seasonRunning = false)
        }
        when (sendDeletes(credentials)) {
            Sent.DONE -> Unit
            Sent.RETRY -> retry = true
            Sent.NO_ACCOUNT -> return SyncRun(retry = false, seasonRunning = false)
        }
        SyncRun(retry, seasonRunning)
    }

    private enum class Sent { DONE, RETRY, NO_ACCOUNT }

    private suspend fun sendUploads(credentials: Credentials): Sent {
        val season = dao.seasonStart()?.let(::calendarOf) ?: return Sent.DONE
        val due = dao.uploads().filter { it.isDue(season) }
        for (batch in due.chunked(UploadSightingsRequest.MAX_SIGHTINGS)) {
            val claimed = dao.claim(batch.map { it.clientId })
            if (claimed.isEmpty()) continue
            val sent = server.uploadSightings(credentials, UploadSightingsRequest(claimed.map { it.toUpload() }))
            val problem = when (sent) {
                is ServerResult.Ok -> {
                    dao.uploaded(claimed.map { it.clientId })
                    continue
                }
                is ServerResult.Failed -> sent.problem
            }
            when {
                problem == UNAUTHORIZED -> return Sent.NO_ACCOUNT
                problem !in REFUSED_SIGHTING -> return Sent.RETRY
                // All or none: one sighting the server will not take holds back the batch. Each goes alone, so
                // the rest get through and the one that cannot is found.
                else -> sendOneByOne(credentials, claimed)?.let { return it }
            }
        }
        return Sent.DONE
    }

    /** Null when every sighting was sent or set aside. */
    private suspend fun sendOneByOne(credentials: Credentials, sightings: List<SyncQueueEntity>): Sent? {
        for (sighting in sightings) {
            when (val sent = server.uploadSightings(credentials, UploadSightingsRequest(listOf(sighting.toUpload())))) {
                is ServerResult.Ok -> dao.uploaded(listOf(sighting.clientId))
                is ServerResult.Failed -> when (sent.problem) {
                    UNAUTHORIZED -> return Sent.NO_ACCOUNT
                    // A phone's clock ahead of the world's: the date is fine tomorrow, so it waits for the next run.
                    ServerProblem.Refused(ApiErrorCode.SIGHTING_DATE_IN_FUTURE) -> Unit
                    in REFUSED_SIGHTING -> dao.refuse(sighting.clientId)
                    else -> return Sent.RETRY
                }
            }
        }
        return null
    }

    private suspend fun sendDeletes(credentials: Credentials): Sent {
        for (delete in dao.deletes()) {
            when (val deleted = server.deleteSighting(credentials, delete.clientId)) {
                is ServerResult.Ok -> dao.deleted(delete.clientId)
                is ServerResult.Failed -> when (deleted.problem) {
                    // Never sent, or deleted already: either way the server does not have it.
                    ServerProblem.Refused(ApiErrorCode.SIGHTING_NOT_FOUND) -> dao.deleted(delete.clientId)
                    UNAUTHORIZED -> return Sent.NO_ACCOUNT
                    else -> return Sent.RETRY
                }
            }
        }
        return Sent.DONE
    }

    private suspend fun setLeague(leagueId: String?) {
        val state = dao.state()
        if (state != null && state.leagueId == leagueId) return
        dao.saveState(SyncStateEntity(leagueId = leagueId, leagueSince = clock.millis()))
    }

    private fun season(): Flow<SeasonCalendar?> = dao.observeSeasonStart().map { it?.let(::calendarOf) }

    private fun calendarOf(seasonStart: String): SeasonCalendar? =
        (SeasonCalendar.of(LocalDate.parse(seasonStart)) as? SeasonCalendarResult.Valid)?.calendar

    private fun SyncQueueEntity.isDue(season: SeasonCalendar?): Boolean = when {
        refused -> false
        action == SyncAction.DELETE.name -> true
        else -> season?.isSentForSeason(LocalDate.parse(date)) == true
    }

    private companion object {
        val UNAUTHORIZED = ServerProblem.Refused(ApiErrorCode.UNAUTHORIZED)
        val BRACKET_NOT_READY = ServerProblem.Refused(ApiErrorCode.BRACKET_NOT_READY)

        /** Refusals of a sighting itself, which sending it again cannot change. */
        val REFUSED_SIGHTING = setOf(
            ServerProblem.Refused(ApiErrorCode.INVALID_REQUEST),
            ServerProblem.Refused(ApiErrorCode.COUNTY_UNKNOWN),
            ServerProblem.Refused(ApiErrorCode.BIRD_UNKNOWN),
            ServerProblem.Refused(ApiErrorCode.SIGHTING_DATE_IN_FUTURE),
        )
    }
}

/** The four fields the server gets (ADR 0001): the count, the time of day and the place name stay here. */
internal fun SyncQueueEntity.toUpload() =
    SightingUpload(clientId = clientId, commonName = commonName, countyCode = countyCode, date = LocalDate.parse(date))
