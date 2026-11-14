package io.cosgriff.flywaybracket.domain.bracket

import io.cosgriff.flywaybracket.domain.pack.CountyChart
import io.cosgriff.flywaybracket.domain.pack.TimeBin

/** What choosing a league's field gave: 64 seeded birds, or too few candidates to start. */
sealed interface FieldResult {
    /**
     * @property teams 64 birds, 16 in each [BirdGroup]'s quarter, seeded 1 to 16
     * @property candidates how many birds could have made the field
     */
    data class Seeded(val teams: List<Team>, val candidates: Int) : FieldResult {
        init {
            require(teams.size == Field.SIZE) { "A field has ${Field.SIZE} birds, not ${teams.size}" }
        }
    }

    /**
     * Fewer than [Field.SIZE] birds could make the field: not enough migrate through the members' home
     * counties during the season. A league whose field this is when its season starts is closed
     * ([io.cosgriff.flywaybracket.domain.league.CloseReason.TOO_FEW_BIRDS]).
     */
    data class TooFewBirds(val candidates: Int) : FieldResult
}

/** A matchup of the bracket by where it is drawn: [position] counts from 0 at the top, within [round]. */
data class DrawPosition(val round: Round, val position: Int)

/**
 * Chooses and seeds a league's 64 birds (#34), and draws them into the bracket.
 *
 * - The candidates are the birds that are migrants ([CountyChart.migrants]) in at least one member's home
 *   county and expected ([CountyChart.isExpected]) in at least one member's home county in at least one
 *   bin of the season.
 * - Each is placed in a quarter by its [BirdGroup] and given its [Seeding.strength] over the members' home
 *   counties.
 * - Each quarter takes its 16 strongest. A quarter with fewer than 16 ([SeedingResult.QuarterShort]) is
 *   filled with the strongest candidates left over from any group, so the field is always 64.
 * - With fewer than 64 candidates in all, there is no field ([FieldResult.TooFewBirds]).
 */
object Field {
    /** Birds in a field. */
    const val SIZE = 64

    /** The quarters, top of the bracket first, by their names in a [Team]. */
    val quarters: List<String> = BirdGroup.entries.map { it.name }

    /**
     * The league's field for a season.
     *
     * @param homeCounties one chart per member whose home county a pack holds: a county two members share
     *   is in the list twice, so it counts twice toward strength. A member whose county no pack holds is
     *   left out.
     * @param bins the bins the season covers ([SeasonCalendar.allBins])
     */
    fun choose(homeCounties: List<CountyChart>, bins: List<TimeBin>): FieldResult = seed(candidates(homeCounties, bins))

    /** The birds that could make the field, each in its group's quarter, in order of name. */
    fun candidates(homeCounties: List<CountyChart>, bins: List<TimeBin>): List<Candidate> {
        val counties = homeCounties.distinctBy { it.regionCode }
        val migrants = counties.flatMapTo(HashSet()) { county -> county.migrants().map { it.commonName } }
        return migrants
            .filter { name -> counties.any { county -> bins.any { bin -> county.isExpected(name, bin) } } }
            .sorted()
            .map { name -> Candidate(name, BirdGroup.of(name).name, Seeding.strength(name, homeCounties, bins)) }
    }

    /** Seeds [candidates] into four quarters of 16, filling a short quarter from the others' leftovers. */
    fun seed(candidates: List<Candidate>): FieldResult {
        if (candidates.size < SIZE) return FieldResult.TooFewBirds(candidates.size)
        return when (val seeded = Seeding.seed(candidates, quarters)) {
            is SeedingResult.Seeded -> FieldResult.Seeded(seeded.teams, candidates.size)
            is SeedingResult.QuarterShort -> {
                val filled = Seeding.seed(fillShortQuarters(candidates), quarters)
                FieldResult.Seeded((filled as SeedingResult.Seeded).teams, candidates.size)
            }
        }
    }

    /**
     * Exactly 64 candidates, 16 to a quarter: each quarter's 16 strongest of its own group, then, quarter by
     * quarter from the top, the strongest left over from any group moved into a quarter that is short.
     */
    private fun fillShortQuarters(candidates: List<Candidate>): List<Candidate> {
        val strongestFirst = compareByDescending<Candidate> { it.strength }.thenBy { it.commonName }
        val own = quarters.associateWith { quarter ->
            candidates.filter { it.quarter == quarter }.sortedWith(strongestFirst).take(Seeding.PER_QUARTER)
        }
        val leftOver = (candidates - own.values.flatten().toSet()).sortedWith(strongestFirst).iterator()
        return quarters.flatMap { quarter ->
            val mine = own.getValue(quarter)
            mine + List(Seeding.PER_QUARTER - mine.size) { leftOver.next().copy(quarter = quarter) }
        }
    }

    /**
     * The 64 birds as the bracket draws them, top to bottom: the quarters in [quarters] order, each in
     * [Seeding.firstRoundOrder]. Each first-round matchup is two neighbours here.
     */
    fun drawOrder(teams: List<Team>): List<Team> = quarters.flatMap { quarter ->
        Seeding.firstRound(teams.filter { it.quarter == quarter }).flatMap { listOf(it.upper, it.lower) }
    }

    /**
     * The quarter a matchup is played in, by its name in [quarters] (#42): each quarter holds a quarter of
     * the matchups of every round up to the quarterfinals, top to bottom. Null for a semifinal or the final,
     * where two quarters meet.
     */
    fun quarterOf(at: DrawPosition): String? {
        val perQuarter = at.round.teams / 2 / quarters.size
        if (perQuarter == 0) return null
        return quarters.getOrNull(at.position / perQuarter)
    }

    /** The 32 first-round matchups, top to bottom. */
    fun firstRound(teams: List<Team>): List<Matchup> = drawOrder(teams).chunked(2).map { (upper, lower) -> Matchup(upper, lower) }

    /**
     * The matchups whose possible birds differ between two fields: a first-round matchup whose two birds
     * changed, and any later matchup that one of its feeding matchups' birds changed. A pick made against
     * [before] is no longer sound for these. Top to bottom within each round, rounds in order.
     */
    fun changedMatchups(before: List<Team>, after: List<Team>): List<DrawPosition> {
        val was = drawOrder(before).map { it.commonName }
        val now = drawOrder(after).map { it.commonName }
        return Round.entries.flatMap { round ->
            val birdsPerMatchup = SIZE / (round.teams / 2)
            (0 until round.teams / 2)
                .filter { position ->
                    val range = position * birdsPerMatchup until (position + 1) * birdsPerMatchup
                    was.slice(range).toSet() != now.slice(range).toSet()
                }
                .map { DrawPosition(round, it) }
        }
    }
}
