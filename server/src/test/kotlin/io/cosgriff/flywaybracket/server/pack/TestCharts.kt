package io.cosgriff.flywaybracket.server.pack

import io.cosgriff.flywaybracket.domain.pack.migrantChart
import io.cosgriff.flywaybracket.domain.pack.testFieldPack
import java.nio.file.Files

/** The made-up home county most tests give their players. No pack file holds it; [testCharts] does. */
const val TEST_HOME_COUNTY = "US-ZZ-001"

/** Packs over a new directory holding one file: the made-up pack of Fieldland, `XX-FD` (`testFieldPack`). */
fun fieldPacks(): Packs {
    val directory = Files.createTempDirectory("field-packs")
    directory.toFile().deleteOnExit()
    writePack(directory, testFieldPack())
    return Packs(directory)
}

/**
 * Fieldland's counties, from [fieldPacks], and [TEST_HOME_COUNTY] with 20 made-up migrants of each group, so
 * a league of players from there can be seeded and start. Any other county is in no pack.
 */
fun testCharts(): CountyCharts {
    val packs = fieldPacks()
    val home = migrantChart(TEST_HOME_COUNTY)
    return CountyCharts { code -> if (code == TEST_HOME_COUNTY) home else packs.chartFor(code) }
}
