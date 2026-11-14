package io.cosgriff.flywaybracket.domain.bracket

import io.cosgriff.flywaybracket.domain.pack.CountyChart
import io.cosgriff.flywaybracket.domain.pack.TimeBin

/** A bird that could make the field, with the quarter it would play in and how common it will be. */
data class Candidate(val commonName: String, val quarter: String, val strength: Double)

/** What seeding gave: four full quarters, or the quarter that came up short. */
sealed interface SeedingResult {
    data class Seeded(val teams: List<Team>) : SeedingResult {
        fun quarter(name: String): List<Team> = teams.filter { it.quarter == name }.sortedBy { it.seed }
    }

    /** [quarter] has only [found] candidates, so it can't field 16. */
    data class QuarterShort(val quarter: String, val found: Int) : SeedingResult
}

/**
 * Builds a league's field. Each quarter takes its 16 strongest candidates and seeds them by strength,
 * so the favorites are the birds most players will see and the low seeds are the long shots.
 */
object Seeding {
    const val PER_QUARTER = 16

    /** Top to bottom of a quarter's first round, so seeds 1 and 2 can only meet in its last game. */
    val firstRoundOrder = listOf(1, 16, 8, 9, 5, 12, 4, 13, 6, 11, 3, 14, 7, 10, 2, 15)

    /**
     * How common a bird will be for a league during a tournament: its best smoothed frequency in
     * [bins], averaged over the members' home counties. A county that doesn't list the bird counts
     * as zero, so a bird only some members can see seeds lower.
     */
    fun strength(commonName: String, homeCounties: List<CountyChart>, bins: List<TimeBin>): Double {
        if (homeCounties.isEmpty() || bins.isEmpty()) return 0.0
        return homeCounties.sumOf { county ->
            county.find(commonName)?.let { species -> bins.maxOf(species::smoothedAt) } ?: 0.0
        } / homeCounties.size
    }

    fun seed(candidates: List<Candidate>, quarters: List<String>): SeedingResult {
        val teams = quarters.flatMap { quarter ->
            val strongest = candidates
                .filter { it.quarter == quarter }
                // Equal strength falls back to the name, so the same data always seeds the same way.
                .sortedWith(compareByDescending<Candidate> { it.strength }.thenBy { it.commonName })
                .take(PER_QUARTER)
            if (strongest.size < PER_QUARTER) return SeedingResult.QuarterShort(quarter, strongest.size)
            strongest.mapIndexed { i, candidate -> Team(candidate.commonName, quarter, seed = i + 1) }
        }
        return SeedingResult.Seeded(teams)
    }

    /** A quarter's eight first-round games, top to bottom. */
    fun firstRound(quarterTeams: List<Team>): List<Matchup> {
        require(quarterTeams.map { it.seed }.sorted() == (1..PER_QUARTER).toList()) {
            "A quarter needs seeds 1 to $PER_QUARTER, one each"
        }
        val bySeed = quarterTeams.associateBy { it.seed }
        return firstRoundOrder.chunked(2).map { (upper, lower) -> Matchup(bySeed.getValue(upper), bySeed.getValue(lower)) }
    }
}
