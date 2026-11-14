package io.cosgriff.flywaybracket.domain.outing

import java.time.LocalDate
import java.time.ZoneId

// The figures Home shows for a season (#25). Each takes the player's outings in any order and counts those
// dated on or after `since`; `zone` is where the player is, which decides an outing's date.

/** The species seen on outings dated on or after [since]. */
fun speciesSince(logs: List<OutingLog>, since: LocalDate, zone: ZoneId): Set<String> =
    logs.filterNot { it.outing.date(zone).isBefore(since) }.flatMapTo(mutableSetOf()) { it.species }

/** How many outings are dated on or after [since]. An outing with no sightings counts. */
fun outingsSince(logs: List<OutingLog>, since: LocalDate, zone: ZoneId): Int =
    logs.count { !it.outing.date(zone).isBefore(since) }

/** The new lifers of the period: species seen on or after [since] that no outing before it had. */
fun newLifersSince(logs: List<OutingLog>, since: LocalDate, zone: ZoneId): Set<String> {
    val before = logs.filter { it.outing.date(zone).isBefore(since) }.flatMapTo(mutableSetOf()) { it.species }
    return speciesSince(logs, since, zone) - before
}
