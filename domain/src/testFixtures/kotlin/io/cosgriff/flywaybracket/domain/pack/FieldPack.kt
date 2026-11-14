package io.cosgriff.flywaybracket.domain.pack

import java.time.LocalDate

// Made-up migrants and a made-up pack with enough of them to seed a league's field (#34). No name or
// figure here is real.

/**
 * [count] made-up migrants named `Test <family> 01`, `Test <family> 02` and so on, each reported through
 * spring (March to May) and fall (August to October) and not at all in between. A higher number is a
 * commoner bird: number n is on 4% + n% of checklists in season.
 */
fun migrants(family: String, count: Int): List<SpeciesFrequency> = (1..count).map { n ->
    val inSeason = (4 + n) / 100.0
    SpeciesFrequency(
        "Test $family ${n.toString().padStart(2, '0')}",
        List(TimeBin.COUNT) { bin -> if (TimeBin(bin).month in SPRING_MONTHS || TimeBin(bin).month in FALL_MONTHS) inSeason else 0.0 },
    )
}

/**
 * A county chart with [warblers] songbirds, [sandpipers] waterbirds, [swallows] fliers and [teal]
 * waterfowl, all [migrants], and one resident, `Test Resident Sparrow`, which is never a candidate.
 */
fun migrantChart(regionCode: String, warblers: Int = 20, sandpipers: Int = 20, swallows: Int = 20, teal: Int = 20) = CountyChart(
    regionCode,
    List(TimeBin.COUNT) { 500 },
    migrants("Warbler", warblers) + migrants("Sandpiper", sandpipers) + migrants("Swallow", swallows) + migrants("Teal", teal) +
        resident("Test Resident Sparrow", 0.5),
)

/**
 * The made-up state of Fieldland, `XX-FD`, for seeding tests:
 *
 * - `XX-FD-001` Plenty: 20 migrants of each group, 80 candidates in spring or fall.
 * - `XX-FD-002` Few Ducks: 20 of each group but only 6 teal, so the waterfowl quarter is filled from the
 *   others.
 * - `XX-FD-003` Sparse: 10 of each group, 40 in all: too few for a field.
 */
fun testFieldPack(): StatePack = StatePack(
    state = PackState("XX-FD", "Fieldland"),
    builtOn = LocalDate.of(2026, 3, 14),
    source = "Made-up figures for tests",
    counties = listOf(
        fieldCounty(migrantChart("XX-FD-001"), "Plenty", west = 0.0),
        fieldCounty(migrantChart("XX-FD-002", teal = 6), "Few Ducks", west = 1.0),
        fieldCounty(migrantChart("XX-FD-003", warblers = 10, sandpipers = 10, swallows = 10, teal = 10), "Sparse", west = 2.0),
    ),
)

private fun fieldCounty(chart: CountyChart, name: String, west: Double) =
    County(name, chart, Outline(listOf(rectangle(west, 0.0, west + 1.0, 1.0))))

private val SPRING_MONTHS = 3..5
private val FALL_MONTHS = 8..10
