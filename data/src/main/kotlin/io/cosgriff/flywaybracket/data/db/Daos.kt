package io.cosgriff.flywaybracket.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Transaction
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

    /** In the order they were first logged. */
    @Query("SELECT * FROM sightings WHERE outingId = :outingId ORDER BY seenAt, id")
    fun observeSightings(outingId: Long): Flow<List<SightingEntity>>

    @Insert
    suspend fun insert(outing: OutingEntity): Long

    /** Starts an outing unless one is in progress; either way, returns the one now in progress. */
    @Transaction
    suspend fun start(placeName: String, countyCode: String, at: Long): OutingEntity =
        inProgress() ?: OutingEntity(placeName = placeName, countyCode = countyCode, startedAt = at, endedAt = null)
            .let { it.copy(id = insert(it)) }

    /** Ends the outing in progress, if there is one. Returns the number of outings ended. */
    @Query("UPDATE outings SET endedAt = :at WHERE endedAt IS NULL")
    suspend fun endInProgress(at: Long): Int

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
    suspend fun removeSighting(id: Long): Int
}
