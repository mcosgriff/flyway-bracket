package io.cosgriff.flywaybracket.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow

@Dao
interface OutingDao {
    @Query("SELECT * FROM outings WHERE endedAt IS NULL ORDER BY id DESC LIMIT 1")
    fun observeInProgress(): Flow<OutingEntity?>

    @Query("SELECT * FROM outings WHERE endedAt IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun inProgress(): OutingEntity?

    /** Newest first. */
    @Query("SELECT * FROM outings WHERE endedAt IS NOT NULL ORDER BY startedAt DESC, id DESC")
    fun observeFinished(): Flow<List<OutingEntity>>

    /** Newest first, each with its sightings in the order they were first logged. */
    @Transaction
    @Query("SELECT * FROM outings WHERE endedAt IS NOT NULL ORDER BY startedAt DESC, id DESC")
    fun observeFinishedWithSightings(): Flow<List<OutingWithSightings>>

    /** One outing with its sightings, or null when there is no outing with this id. */
    @Transaction
    @Query("SELECT * FROM outings WHERE id = :id")
    fun observeWithSightings(id: Long): Flow<OutingWithSightings?>

    /** In the order they were first logged. */
    @Query("SELECT * FROM sightings WHERE outingId = :outingId ORDER BY seenAt, id")
    fun observeSightings(outingId: Long): Flow<List<SightingEntity>>

    /**
     * The birds with a sighting first logged from [from] up to, not including, [until], in milliseconds since
     * the epoch, on any outing: each bird once.
     */
    @Query("SELECT DISTINCT birdName FROM sightings WHERE seenAt >= :from AND seenAt < :until")
    fun observeBirdsSeenBetween(from: Long, until: Long): Flow<List<String>>

    @Insert
    suspend fun insert(outing: OutingEntity): Long

    /** Starts an outing unless one is in progress; either way, returns the one now in progress. */
    @Transaction
    suspend fun start(placeName: String, countyCode: String, at: Long): OutingEntity =
        inProgress() ?: OutingEntity(placeName = placeName, countyCode = countyCode, startedAt = at, endedAt = null)
            .let { it.copy(id = insert(it)) }

    /** Renames the outing in progress, if there is one. Returns the number of outings renamed. */
    @Query("UPDATE outings SET placeName = :placeName WHERE endedAt IS NULL")
    suspend fun renameInProgress(placeName: String): Int

    /** Moves the outing in progress to another county, if there is one. Returns the number of outings moved. */
    @Query("UPDATE outings SET countyCode = :countyCode WHERE endedAt IS NULL")
    suspend fun setCountyInProgress(countyCode: String): Int

    /** Ends the outing in progress, if there is one. Returns the number of outings ended. */
    @Query("UPDATE outings SET endedAt = :at WHERE endedAt IS NULL")
    suspend fun endInProgress(at: Long): Int

    @Query("SELECT * FROM sightings WHERE outingId = :outingId")
    suspend fun sightingsOf(outingId: Long): List<SightingEntity>

    @Query("SELECT * FROM outings WHERE id = :id")
    suspend fun outing(id: Long): OutingEntity?

    @Query("SELECT * FROM sightings WHERE id = :id")
    suspend fun sightingById(id: Long): SightingEntity?

    @Upsert
    suspend fun queue(entries: List<SyncQueueEntity>)

    @Query("SELECT * FROM sync_queue WHERE clientId = :clientId")
    suspend fun queued(clientId: String): SyncQueueEntity?

    @Query("DELETE FROM sync_queue WHERE clientId = :clientId")
    suspend fun unqueue(clientId: String)

    /**
     * Ends the outing in progress, if there is one, and queues an upload of each of its sightings (#43), dated
     * the day it was first logged in [zone]. Returns the number of outings ended.
     */
    @Transaction
    suspend fun finish(at: Long, zone: ZoneId): Int {
        val outing = inProgress() ?: return 0
        endInProgress(at)
        queue(sightingsOf(outing.id).map { it.upload(outing.countyCode, zone, at) })
        return 1
    }

    /** Deletes the outing in progress and its sightings. A finished outing is never touched. */
    @Query("DELETE FROM outings WHERE endedAt IS NULL")
    suspend fun discardInProgress(): Int

    @Query("SELECT * FROM sightings WHERE outingId = :outingId AND birdName = :birdName")
    suspend fun sighting(outingId: Long, birdName: String): SightingEntity?

    @Insert
    suspend fun insert(sighting: SightingEntity): Long

    @Query("UPDATE sightings SET count = :count WHERE id = :id")
    suspend fun setCount(id: Long, count: Int): Int

    /** Logs one more of a bird on an outing: a new sighting of 1, or one added to the sighting already there. */
    @Transaction
    suspend fun addOrBump(outingId: Long, birdName: String, at: Long): SightingEntity {
        val seen = sighting(outingId, birdName)
        if (seen == null) {
            val added = SightingEntity(outingId = outingId, birdName = birdName, count = 1, seenAt = at)
            return added.copy(id = insert(added))
        }
        setCount(seen.id, seen.count + 1)
        return seen.copy(count = seen.count + 1)
    }

    @Query("DELETE FROM sightings WHERE id = :id")
    suspend fun deleteSighting(id: Long): Int

    /**
     * Deletes a sighting. Of a finished outing, the server may have it (#43): an upload never tried is taken
     * off the queue, and anything else is queued as a delete at [at]. A sighting of the outing in progress
     * was never queued.
     */
    @Transaction
    suspend fun removeSighting(id: Long, at: Long, zone: ZoneId) {
        val sighting = sightingById(id) ?: return
        deleteSighting(id)
        val outing = outing(sighting.outingId) ?: return
        if (outing.endedAt == null) return
        val queued = queued(sighting.clientId)
        if (queued != null && queued.action == SyncAction.UPLOAD.name && !queued.mayBeOnServer) {
            unqueue(sighting.clientId)
        } else {
            val upload = queued ?: sighting.upload(outing.countyCode, zone, at)
            queue(listOf(upload.copy(action = SyncAction.DELETE.name, queuedAt = at, mayBeOnServer = true, refused = false)))
        }
    }
}

/** An upload of this sighting, logged in [countyCode], queued at [at]. */
internal fun SightingEntity.upload(countyCode: String, zone: ZoneId, at: Long) = SyncQueueEntity(
    clientId = clientId,
    action = SyncAction.UPLOAD.name,
    commonName = birdName,
    countyCode = countyCode,
    date = Instant.ofEpochMilli(seenAt).atZone(zone).toLocalDate().toString(),
    queuedAt = at,
)
