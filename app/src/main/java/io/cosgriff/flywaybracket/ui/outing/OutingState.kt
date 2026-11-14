package io.cosgriff.flywaybracket.ui.outing

import androidx.compose.runtime.Immutable
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/** A county by its name without "County" and its state's postal abbreviation: `Jefferson`, `CO`. */
@Immutable
data class OutingCounty(val name: String, val stateAbbreviation: String)

/**
 * What the Outing screen shows of the outing in progress. The time since it started is passed beside
 * this, since it changes every second.
 *
 * @property place the place name the player gave; empty when they gave none
 * @property county null when the county's name is not known on this phone
 * @property sightings in the order the birds were first logged, the latest first
 */
@Immutable
data class OutingState(
    val place: String = "",
    val county: OutingCounty? = null,
    val sightings: List<SightingRow> = emptyList(),
) {
    val species: Int get() = sightings.size
    val birds: Int get() = sightings.sumOf { it.count }
}

/** A bird on the outing. [seenAt] is the local time it was first logged. */
@Immutable
data class SightingRow(val id: Long, val bird: String, val count: Int, val seenAt: LocalTime)

/**
 * What the Add sighting sheet lists: the birds of the county's pack, already in order and narrowed by
 * the search (`searchBirds` in `:domain`).
 *
 * @property hasPack false when no installed pack holds the outing's county
 */
@Immutable
data class AddSightingState(
    val county: OutingCounty? = null,
    val birds: List<BirdRow> = emptyList(),
    val hasPack: Boolean = true,
)

/**
 * A bird the player can tap to log.
 *
 * @property expected whether it is expected in the county this week
 * @property logged how many are on the outing already; 0 when none
 */
@Immutable
data class BirdRow(val name: String, val expected: Boolean, val logged: Int = 0)

/** A finished outing, as the Summary screen shows it. */
@Immutable
data class SummaryState(
    val place: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val length: Duration,
    /** The birds seen, in the order first logged. */
    val sightings: List<SummaryRow>,
) {
    val species: Int get() = sightings.size
    val birds: Int get() = sightings.sumOf { it.count }
}

@Immutable
data class SummaryRow(val bird: String, val count: Int)
