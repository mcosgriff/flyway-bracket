package io.cosgriff.flywaybracket.domain.pack

import java.time.LocalDate

// A made-up state pack for tests, in this module and in others. No state, county or figure here is real.

/** A ring around the rectangle from ([west], [south]) to ([east], [north]). */
fun rectangle(west: Double, south: Double, east: Double, north: Double): List<LonLat> = listOf(
    LonLat(west, south),
    LonLat(east, south),
    LonLat(east, north),
    LonLat(west, north),
)

fun county(code: String, name: String, species: List<SpeciesFrequency>, vararg rings: List<LonLat>) =
    County(name, CountyChart(code, List(TimeBin.COUNT) { 400 + it }, species), Outline(rings.toList()))

/**
 * The made-up state of Testland, `XX-TS`, with three counties in a row:
 *
 * - `XX-TS-001` West Square: longitude 0 to 1, latitude 0 to 1.
 * - `XX-TS-002` East Square: longitude 1 to 2, latitude 0 to 1.
 * - `XX-TS-003` Twin Islands: two squares of the same height, longitude 3 to 4 and longitude 5 to 6.
 *
 * Every frequency has four decimal places or fewer and no bird is too rare to keep, so
 * [StatePackCodec] gives back an equal pack.
 */
fun testStatePack(): StatePack {
    val mayStart = TimeBin.of(5, 1)
    val mayEnd = TimeBin.of(5, 4)
    val septemberStart = TimeBin.of(9, 1)
    val septemberEnd = TimeBin.of(9, 4)
    val warbler = seasonal("Test Warbler", mayStart, mayEnd, inSeason = 0.2575)
    val sandpiper = seasonal("Test Sandpiper", septemberStart, septemberEnd, inSeason = 0.12, offSeason = 0.0031)
    val sparrow = resident("Test Sparrow", 0.6)
    val tern = seasonal("Test Tern", mayStart, septemberEnd, inSeason = 0.0425)
    return StatePack(
        state = PackState("XX-TS", "Testland"),
        builtOn = LocalDate.of(2026, 3, 14),
        source = "Made-up figures for tests",
        counties = listOf(
            county("XX-TS-001", "West Square", listOf(warbler, sparrow), rectangle(0.0, 0.0, 1.0, 1.0)),
            county("XX-TS-002", "East Square", listOf(sandpiper, sparrow), rectangle(1.0, 0.0, 2.0, 1.0)),
            county(
                "XX-TS-003",
                "Twin Islands",
                listOf(tern, sandpiper),
                rectangle(3.0, 0.0, 4.0, 1.0),
                rectangle(5.0, 0.0, 6.0, 1.0),
            ),
        ),
    )
}
