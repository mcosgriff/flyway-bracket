package io.cosgriff.flywaybracket.data

import io.cosgriff.flywaybracket.data.db.OutingDao
import io.cosgriff.flywaybracket.data.db.OutingEntity
import io.cosgriff.flywaybracket.data.db.SightingEntity
import io.cosgriff.flywaybracket.domain.outing.Outing
import io.cosgriff.flywaybracket.domain.outing.OutingLog
import io.cosgriff.flywaybracket.domain.outing.Sighting
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The player's outings and the birds seen on them, kept on the phone only (#25). At most one outing is in
 * progress. [clock] gives the time an outing starts and ends and a bird is logged. Made by `DataModule`.
 */
class OutingRepository(private val dao: OutingDao, private val clock: Clock) {
    /** The outing in progress, or `null`. */
    fun inProgress(): Flow<Outing?> = dao.observeInProgress().map { it?.toDomain() }

    /** Finished outings, newest first. */
    fun finished(): Flow<List<Outing>> = dao.observeFinished().map { outings -> outings.map { it.toDomain() } }

    /** Finished outings, newest first, each with its sightings. */
    fun finishedLogs(): Flow<List<OutingLog>> = dao.observeFinishedWithSightings().map { logs ->
        logs.map { log -> OutingLog(log.outing.toDomain(), log.sightings.sortedWith(FIRST_LOGGED).map { it.toDomain() }) }
    }

    /** One outing with its sightings in the order the birds were first logged, or `null` when it is gone. */
    fun log(outingId: Long): Flow<OutingLog?> = dao.observeWithSightings(outingId).map { log ->
        log?.let { OutingLog(it.outing.toDomain(), it.sightings.sortedWith(FIRST_LOGGED).map { sighting -> sighting.toDomain() }) }
    }

    /** The sightings of one outing, in the order the birds were first logged. */
    fun sightings(outingId: Long): Flow<List<Sighting>> =
        dao.observeSightings(outingId).map { sightings -> sightings.map { it.toDomain() } }

    /**
     * Starts an outing now and returns it. When one is already in progress nothing is started and that one
     * is returned, so check the returned outing rather than assume it has [placeName] and [countyCode].
     *
     * @param placeName free text, stored trimmed; may be empty.
     * @param countyCode eBird region code of the county (`US-CO-059`).
     */
    suspend fun start(placeName: String, countyCode: String): Outing {
        require(countyCode.isNotBlank()) { "an outing needs a county" }
        return dao.start(placeName.trim(), countyCode.trim(), now()).toDomain()
    }

    /**
     * Logs one of a bird on an outing: a new sighting with a count of 1, or one more on the sighting the
     * outing already has for that bird. The sighting keeps the time the bird was first logged.
     */
    suspend fun addOrBump(outingId: Long, birdName: String): Sighting {
        require(birdName.isNotBlank()) { "a sighting needs a bird" }
        return dao.addOrBump(outingId, birdName.trim(), now()).toDomain()
    }

    /** Sets how many of a bird were seen. To take a bird off the outing, use [remove]. */
    suspend fun setCount(sightingId: Long, count: Int) {
        require(count >= Sighting.MIN_COUNT) { "a sighting counts at least ${Sighting.MIN_COUNT} bird, not $count" }
        dao.setCount(sightingId, count)
    }

    suspend fun remove(sightingId: Long) {
        dao.removeSighting(sightingId)
    }

    /** Gives the outing in progress a new place name, stored trimmed; may be empty. Nothing happens when none is. */
    suspend fun rename(placeName: String) {
        dao.renameInProgress(placeName.trim())
    }

    /**
     * Moves the outing in progress to another county, chosen by the player (#27). Nothing happens when none
     * is.
     *
     * @param countyCode eBird region code of the county (`US-CO-059`).
     */
    suspend fun setCounty(countyCode: String) {
        require(countyCode.isNotBlank()) { "an outing needs a county" }
        dao.setCountyInProgress(countyCode.trim())
    }

    /** Ends the outing in progress now. Nothing happens when none is. */
    suspend fun end() {
        dao.endInProgress(now())
    }

    /** Deletes the outing in progress with its sightings. A finished outing is never deleted. */
    suspend fun discard() {
        dao.discardInProgress()
    }

    private fun now(): Long = clock.millis()

    private companion object {
        val FIRST_LOGGED = compareBy<SightingEntity>({ it.seenAt }, { it.id })
    }
}

private fun OutingEntity.toDomain() = Outing(
    id = id,
    placeName = placeName,
    countyCode = countyCode,
    startedAt = Instant.ofEpochMilli(startedAt),
    endedAt = endedAt?.let(Instant::ofEpochMilli),
)

private fun SightingEntity.toDomain() =
    Sighting(id = id, outingId = outingId, birdName = birdName, count = count, seenAt = Instant.ofEpochMilli(seenAt))
