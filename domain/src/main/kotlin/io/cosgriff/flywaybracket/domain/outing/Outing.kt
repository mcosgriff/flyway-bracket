package io.cosgriff.flywaybracket.domain.outing

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One trip outside to look for birds. Only one outing is in progress at a time.
 *
 * @property placeName what the player called the place; free text, and empty when they gave none.
 * @property countyCode eBird region code of the county the outing was in (`US-CO-059`).
 * @property endedAt `null` while the outing is in progress.
 */
data class Outing(
    val id: Long,
    val placeName: String,
    val countyCode: String,
    val startedAt: Instant,
    val endedAt: Instant?,
) {
    init {
        require(endedAt == null || !endedAt.isBefore(startedAt)) { "an outing cannot end before it starts" }
    }

    val inProgress: Boolean get() = endedAt == null

    /** The outing's date for the game: the local date it started, even when it ran past midnight. */
    fun date(zone: ZoneId): LocalDate = startedAt.atZone(zone).toLocalDate()
}

/**
 * A bird seen on an outing. A bird logged again on the same outing is the same sighting with a higher
 * [count], so an outing has at most one sighting per bird.
 *
 * @property birdName the bird's common name, which is what makes two sightings the same species.
 * @property count how many were seen: 1 or more.
 */
data class Sighting(
    val id: Long,
    val outingId: Long,
    val birdName: String,
    val count: Int,
    val seenAt: Instant,
) {
    init {
        require(count >= MIN_COUNT) { "a sighting counts at least $MIN_COUNT bird, not $count" }
    }

    companion object {
        const val MIN_COUNT = 1
    }
}

/** An outing with the birds seen on it. */
data class OutingLog(val outing: Outing, val sightings: List<Sighting>) {
    /** The species seen on the outing, by common name. */
    val species: Set<String> get() = sightings.mapTo(mutableSetOf()) { it.birdName }

    /** How many birds were counted, every sighting's count added up. */
    val birds: Int get() = sightings.sumOf { it.count }
}
