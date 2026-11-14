package io.cosgriff.flywaybracket.domain.pack

/**
 * How often one species is reported in a county through the year: for each [TimeBin], the share of
 * complete checklists (0 to 1) that include it.
 */
data class SpeciesFrequency(val commonName: String, val frequency: List<Double>) {
    init {
        require(frequency.size == TimeBin.COUNT) { "$commonName has ${frequency.size} bins, not ${TimeBin.COUNT}" }
    }

    fun at(bin: TimeBin): Double = frequency[bin.index]

    /**
     * The frequency averaged with the bins either side, so one odd week neither makes nor hides a
     * season. The year wraps.
     */
    fun smoothedAt(bin: TimeBin): Double = (at(bin.previous()) + at(bin) + at(bin.next())) / 3

    val smoothedPeak: Double get() = allBins.maxOf(::smoothedAt)

    val smoothedLow: Double get() = allBins.minOf(::smoothedAt)

    private companion object {
        val allBins = List(TimeBin.COUNT, ::TimeBin)
    }
}

/**
 * One county's birds through the year. A bird pack is a state's worth of these; the county a player
 * stands in picks the chart.
 *
 * @param regionCode the eBird region code, such as `US-CO-059`
 * @param checklists how many checklists each bin's frequencies were counted from
 */
data class CountyChart(
    val regionCode: String,
    val checklists: List<Int>,
    val species: List<SpeciesFrequency>,
) {
    init {
        require(checklists.size == TimeBin.COUNT) { "$regionCode has ${checklists.size} sample sizes" }
    }

    private val byName = species.associateBy { it.commonName }

    fun find(commonName: String): SpeciesFrequency? = byName[commonName]

    /**
     * Whether a bird is expected here in this bin: reported on at least [floor] of checklists. A bird
     * the chart doesn't list is never expected. First version: a plain yes or no (no weighting by how
     * hard the bird is to find).
     */
    fun isExpected(commonName: String, bin: TimeBin, floor: Double = EXPECTED_FLOOR): Boolean =
        (find(commonName)?.at(bin) ?: 0.0) >= floor

    /** The birds expected here in this bin, most often reported first. */
    fun expected(bin: TimeBin, floor: Double = EXPECTED_FLOOR): List<SpeciesFrequency> =
        species.filter { it.at(bin) >= floor }.sortedByDescending { it.at(bin) }

    /**
     * The birds that come and go: common enough at their peak to play for, and nearly absent at some
     * other time of year. Residents, steady all year, are left out, so a bracket's matchups move with
     * migration.
     */
    fun migrants(floor: Double = EXPECTED_FLOOR, lowShare: Double = MIGRANT_LOW_SHARE): List<SpeciesFrequency> =
        species.filter { it.smoothedPeak >= floor && it.smoothedLow <= it.smoothedPeak * lowShare }

    companion object {
        /** 3% of checklists. */
        const val EXPECTED_FLOOR = 0.03

        /** A migrant's quietest stretch is at most this share of its peak. */
        const val MIGRANT_LOW_SHARE = 0.15
    }
}
