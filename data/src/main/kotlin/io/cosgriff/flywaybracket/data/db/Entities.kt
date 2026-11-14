package io.cosgriff.flywaybracket.data.db

import androidx.room3.Entity
import androidx.room3.PrimaryKey

/**
 * A birding outing: the first table, so the database has a schema (#18). Later issues add its sightings.
 *
 * @property countyCode eBird region code of the county the outing was in (`US-CO-059`).
 * @property day the outing's date, as an epoch day.
 */
@Entity(tableName = "outings")
data class OutingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val countyCode: String,
    val day: Long,
)
