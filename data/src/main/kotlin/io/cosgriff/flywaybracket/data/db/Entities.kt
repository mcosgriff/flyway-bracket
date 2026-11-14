package io.cosgriff.flywaybracket.data.db

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation
import java.util.UUID

/**
 * One trip outside (`domain.outing.Outing`). Times are milliseconds since the epoch.
 *
 * @property placeName free text; empty when the player gave none.
 * @property countyCode eBird region code of the county the outing was in (`US-CO-059`).
 * @property endedAt `null` while the outing is in progress.
 */
@Entity(tableName = "outings")
data class OutingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val placeName: String,
    val countyCode: String,
    val startedAt: Long,
    val endedAt: Long?,
)

/**
 * A bird seen on an outing (`domain.outing.Sighting`). The unique index keeps one row per bird on an outing:
 * logging the bird again raises [count]. Deleting the outing deletes its sightings.
 *
 * @property seenAt when the bird was first logged, in milliseconds since the epoch.
 * @property clientId the sighting's id on the league server (`SightingUpload.clientId`, #43): a random UUID
 *   made when the bird is first logged, which never changes.
 */
@Entity(
    tableName = "sightings",
    foreignKeys = [
        ForeignKey(entity = OutingEntity::class, parentColumns = ["id"], childColumns = ["outingId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["outingId", "birdName"], unique = true), Index(value = ["clientId"], unique = true)],
)
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val outingId: Long,
    val birdName: String,
    val count: Int,
    val seenAt: Long,
    val clientId: String = UUID.randomUUID().toString(),
)

/** What a [SyncQueueEntity] asks of the server. */
enum class SyncAction {
    /** `POST /v1/sightings`: send the sighting. */
    UPLOAD,

    /** `DELETE /v1/sightings/{clientId}`: the player removed a sighting the server may have. */
    DELETE,
}

/**
 * A sighting waiting to be sent to the league server, or to be deleted there (#43). One row per sighting:
 * a finished outing queues an [SyncAction.UPLOAD] of each of its sightings, and removing a sighting the server
 * may have turns its row into a [SyncAction.DELETE]. A row leaves the queue when the server has taken it. Not
 * tied to the sightings table: a delete outlives its sighting.
 *
 * The bird, county and date are what `SightingUpload` sends, and all that is sent; the date is the day the
 * bird was first logged, in the phone's zone when the outing ended.
 *
 * @property action a [SyncAction] name.
 * @property date an ISO date (`2027-03-01`).
 * @property queuedAt when the row was queued, in milliseconds since the epoch.
 * @property mayBeOnServer whether an upload of it has been tried, so the server may hold it even without an
 *   answer: removing it then needs a delete.
 * @property refused whether the server refused the upload for good (an unknown bird or county): it stays on
 *   the phone and is not sent again.
 */
@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey val clientId: String,
    val action: String,
    val commonName: String,
    val countyCode: String,
    val date: String,
    val queuedAt: Long,
    val mayBeOnServer: Boolean = false,
    val refused: Boolean = false,
)

/**
 * What the background sync last learned (#43): one row, [id] 0.
 *
 * @property leagueId the player's league, as their profile last named it; null when they are in none.
 *   Sightings dated inside its season are the ones sent.
 * @property leagueSince when [leagueId] was last set to a new league, in milliseconds since the epoch: the
 *   time a sighting from before then began to wait.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val id: Int = 0,
    val leagueId: String?,
    val leagueSince: Long,
)

data class OutingWithSightings(
    @Embedded val outing: OutingEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["outingId"]) val sightings: List<SightingEntity>,
)
