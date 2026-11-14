package io.cosgriff.flywaybracket.domain.pack

import java.time.LocalDate

/**
 * One US state's bird data, as it travels in a `<state code>.fbpack` file ([StatePackCodec]): the
 * state's counties, each with its bar chart and its outline.
 *
 * @param builtOn the day the pack was built from its source
 * @param source where the figures came from, for attribution; free text
 */
data class StatePack(
    val state: PackState,
    val builtOn: LocalDate,
    val source: String,
    val counties: List<County>,
) {
    /** The name of this state's pack file, such as `US-CO.fbpack`. */
    val fileName: String get() = state.code + FILE_EXTENSION

    /** The county this place is in, or null when it is in none of the pack's counties. */
    fun countyAt(longitude: Double, latitude: Double): County? =
        counties.firstOrNull { it.outline.contains(longitude, latitude) }

    companion object {
        const val FILE_EXTENSION = ".fbpack"
    }
}

/** @param code the eBird region code, such as `US-CO` */
data class PackState(val code: String, val name: String)

/** A county in a pack. Its code is its chart's region code, such as `US-CO-059`. */
data class County(val name: String, val chart: CountyChart, val outline: Outline) {
    val code: String get() = chart.regionCode
}

/** A place on the map, in degrees. */
data class LonLat(val longitude: Double, val latitude: Double)

/**
 * The edge of a county: one ring of corners for each piece of it (a county with an island has two).
 * A ring may repeat its first corner at the end or leave it out. Version 1 has no holes.
 */
data class Outline(val rings: List<List<LonLat>>) {
    init {
        require(isValid(rings)) { "An outline needs a ring, and each ring $MIN_CORNERS corners or more" }
    }

    /**
     * Whether a place is inside any of the rings, by ray casting: a line drawn east from the place
     * crosses a ring's edge an odd number of times when the place is inside that ring. A place exactly
     * on an edge may land on either side.
     */
    fun contains(longitude: Double, latitude: Double): Boolean =
        rings.any { ringContains(it, longitude, latitude) }

    private fun ringContains(ring: List<LonLat>, longitude: Double, latitude: Double): Boolean {
        var inside = false
        var from = ring.last()
        for (to in ring) {
            // Only an edge with one end north of the place and one end not can cross the line.
            if ((from.latitude > latitude) != (to.latitude > latitude)) {
                val along = (latitude - from.latitude) / (to.latitude - from.latitude)
                val crossing = from.longitude + along * (to.longitude - from.longitude)
                if (longitude < crossing) inside = !inside
            }
            from = to
        }
        return inside
    }

    companion object {
        const val MIN_CORNERS = 3

        fun isValid(rings: List<List<LonLat>>): Boolean = rings.isNotEmpty() && rings.all { it.size >= MIN_CORNERS }
    }
}
