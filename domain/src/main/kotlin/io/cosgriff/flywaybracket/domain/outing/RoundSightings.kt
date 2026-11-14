package io.cosgriff.flywaybracket.domain.outing

import java.time.LocalDate
import java.time.ZoneId

/**
 * The player's sightings dated within a round, reduced to what a tally reads (#42): the counties they were
 * logged in and the birds. With the charts of [counties], it makes the player's
 * [io.cosgriff.flywaybracket.domain.bracket.PlayerRound].
 *
 * @property counties the county codes of the outings with a sighting dated within the round
 * @property birds the common names of the birds with a sighting dated within the round, in any county
 */
data class RoundSightings(val counties: Set<String>, val birds: Set<String>) {
    companion object {
        /**
         * The sightings of [logs] dated within [dates]. A sighting is dated by when it was first logged, in
         * [zone], as the bracket's "not seen yet" dates it; an outing with no sighting in the round adds no
         * county.
         */
        fun of(logs: List<OutingLog>, dates: ClosedRange<LocalDate>, zone: ZoneId): RoundSightings {
            val counties = LinkedHashSet<String>()
            val birds = LinkedHashSet<String>()
            for (log in logs) {
                val within = log.sightings.filter { it.seenAt.atZone(zone).toLocalDate() in dates }
                if (within.isEmpty()) continue
                counties += log.outing.countyCode
                within.mapTo(birds) { it.birdName }
            }
            return RoundSightings(counties, birds)
        }
    }
}
