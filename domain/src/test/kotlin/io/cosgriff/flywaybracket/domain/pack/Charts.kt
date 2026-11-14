package io.cosgriff.flywaybracket.domain.pack

// Made-up charts for tests. No figure here comes from eBird.

/** A bird reported at [inSeason] from [from] to [to] (bins, inclusive) and at [offSeason] otherwise. */
fun seasonal(name: String, from: TimeBin, to: TimeBin, inSeason: Double, offSeason: Double = 0.0) =
    SpeciesFrequency(name, List(TimeBin.COUNT) { if (it in from.index..to.index) inSeason else offSeason })

/** A bird reported at the same rate all year. */
fun resident(name: String, frequency: Double) = SpeciesFrequency(name, List(TimeBin.COUNT) { frequency })

fun chart(vararg species: SpeciesFrequency, regionCode: String = "XX-TEST-001") =
    CountyChart(regionCode, List(TimeBin.COUNT) { 1000 }, species.toList())
