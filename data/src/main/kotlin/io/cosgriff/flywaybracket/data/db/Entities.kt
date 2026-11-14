package io.cosgriff.flywaybracket.data.db

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import androidx.room3.Relation

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
 */
@Entity(
    tableName = "sightings",
    foreignKeys = [
        ForeignKey(entity = OutingEntity::class, parentColumns = ["id"], childColumns = ["outingId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["outingId", "birdName"], unique = true)],
)
data class SightingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val outingId: Long,
    val birdName: String,
    val count: Int,
    val seenAt: Long,
)

data class OutingWithSightings(
    @Embedded val outing: OutingEntity,
    @Relation(parentColumns = ["id"], entityColumns = ["outingId"]) val sightings: List<SightingEntity>,
)
