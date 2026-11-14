package io.cosgriff.flywaybracket.data.db

import androidx.room3.Dao
import androidx.room3.Query
import androidx.room3.Transaction
import androidx.room3.Upsert
import kotlinx.coroutines.flow.Flow

/** The queue of sightings to send to the league server and to delete there, and what the sync knows (#43). */
@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_state WHERE id = 0")
    suspend fun state(): SyncStateEntity?

    @Upsert
    suspend fun saveState(state: SyncStateEntity)

    /** The first Monday of the player's league's season (an ISO date); null with no league or no bracket yet. */
    @Query("SELECT b.seasonStart FROM sync_state s JOIN brackets b ON b.leagueId = s.leagueId WHERE s.id = 0")
    fun observeSeasonStart(): Flow<String?>

    @Query("SELECT b.seasonStart FROM sync_state s JOIN brackets b ON b.leagueId = s.leagueId WHERE s.id = 0")
    suspend fun seasonStart(): String?

    @Query("SELECT leagueSince FROM sync_state WHERE id = 0")
    fun observeLeagueSince(): Flow<Long?>

    /** Every row the server has not refused, oldest first. */
    @Query("SELECT * FROM sync_queue WHERE refused = 0 ORDER BY queuedAt, clientId")
    fun observeQueue(): Flow<List<SyncQueueEntity>>

    /** Uploads the server has not refused, oldest first. */
    @Query("SELECT * FROM sync_queue WHERE action = 'UPLOAD' AND refused = 0 ORDER BY queuedAt, clientId")
    suspend fun uploads(): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE action = 'DELETE' ORDER BY queuedAt, clientId")
    suspend fun deletes(): List<SyncQueueEntity>

    @Query("UPDATE sync_queue SET mayBeOnServer = 1 WHERE action = 'UPLOAD' AND clientId IN (:clientIds)")
    suspend fun markTried(clientIds: List<String>)

    @Query("SELECT * FROM sync_queue WHERE action = 'UPLOAD' AND clientId IN (:clientIds) ORDER BY queuedAt, clientId")
    suspend fun uploadsOf(clientIds: List<String>): List<SyncQueueEntity>

    /**
     * Marks the uploads of [clientIds] as tried, before they are sent, and returns those still queued as
     * uploads: a sighting removed in the meantime is left out, and one removed later needs a delete.
     */
    @Transaction
    suspend fun claim(clientIds: List<String>): List<SyncQueueEntity> {
        markTried(clientIds)
        return uploadsOf(clientIds)
    }

    /** The server has taken these uploads. A delete queued in their place since stays. */
    @Query("DELETE FROM sync_queue WHERE action = 'UPLOAD' AND clientId IN (:clientIds)")
    suspend fun uploaded(clientIds: List<String>)

    @Query("UPDATE sync_queue SET refused = 1 WHERE action = 'UPLOAD' AND clientId = :clientId")
    suspend fun refuse(clientId: String)

    /** The server no longer has this sighting. */
    @Query("DELETE FROM sync_queue WHERE action = 'DELETE' AND clientId = :clientId")
    suspend fun deleted(clientId: String)

    /**
     * The uploads still queued of an outing's sightings, refused ones included. Its other sightings have been
     * sent.
     */
    @Query(
        "SELECT q.* FROM sync_queue q JOIN sightings s ON s.clientId = q.clientId " +
            "WHERE s.outingId = :outingId AND q.action = 'UPLOAD' ORDER BY q.queuedAt, q.clientId",
    )
    fun observeOutingQueue(outingId: Long): Flow<List<SyncQueueEntity>>

    @Query("SELECT COUNT(*) FROM sightings WHERE outingId = :outingId")
    fun observeSightingCount(outingId: Long): Flow<Int>
}
