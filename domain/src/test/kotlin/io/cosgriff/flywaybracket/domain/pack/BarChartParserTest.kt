package io.cosgriff.flywaybracket.domain.pack

import io.cosgriff.flywaybracket.domain.pack.BarChartParseResult.Parsed
import io.cosgriff.flywaybracket.domain.pack.BarChartParseResult.Reason
import io.cosgriff.flywaybracket.domain.pack.BarChartParseResult.Rejected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarChartParserTest {
    // The file's shape with made-up figures: header lines, month row, sample sizes, then taxa. Every
    // row ends with a tab, as the download does.
    private fun row(label: String, figure: (Int) -> String) =
        label + "\t" + (0 until TimeBin.COUNT).joinToString("\t", transform = figure) + "\t"

    private fun file(vararg taxa: String) = listOf(
        "",
        "Frequency of observations in the selected location(s).:",
        "Number of taxa: \t${taxa.size}",
        "",
        "\tJan\t\t\t\tFeb",
        row("Sample Size:") { "${1000 + it}.0" },
        "",
        *taxa,
    ).joinToString("\n")

    private fun parsed(text: String): CountyChart = (BarChartParser.parse("XX-TEST-001", text) as Parsed).chart

    @Test
    fun `reads sample sizes and frequencies`() {
        val chart = parsed(file(row("Test Warbler") { if (it == 17) "0.25" else "0.0" }))
        assertEquals("XX-TEST-001", chart.regionCode)
        assertEquals(1000, chart.checklists.first())
        assertEquals(1047, chart.checklists.last())
        assertEquals(0.25, chart.find("Test Warbler")!!.at(TimeBin(17)), 0.0)
        assertEquals(0.0, chart.find("Test Warbler")!!.at(TimeBin(16)), 0.0)
    }

    @Test
    fun `reads scientific notation`() {
        val chart = parsed(file(row("Test Goose") { "9.16E-4" }))
        assertEquals(0.000916, chart.find("Test Goose")!!.at(TimeBin(0)), 1e-12)
    }

    @Test
    fun `drops the scientific name markup older downloads carry`() {
        val chart = parsed(file(row("Test Goose (<em class=\"sci\">Anser exempli</em>)") { "0.1" }))
        assertEquals(listOf("Test Goose"), chart.species.map { it.commonName })
    }

    @Test
    fun `keeps species and leaves out hybrids, sp, either-or pairs and domestic types`() {
        val chart = parsed(
            file(
                row("Test Goose") { "0.1" },
                row("Test x Other Goose (hybrid)") { "0.1" },
                row("Test/Other Goose") { "0.1" },
                row("goose sp.") { "0.1" },
                row("Domestic goose sp. (Domestic type)") { "0.1" },
                row("Rock Pigeon (Feral Pigeon)") { "0.1" },
            ),
        )
        assertEquals(listOf("Test Goose", "Rock Pigeon (Feral Pigeon)"), chart.species.map { it.commonName })
    }

    @Test
    fun `text without a sample size row is not a bar chart`() {
        assertEquals(Rejected(Reason.NO_SAMPLE_SIZE_ROW), BarChartParser.parse("XX", "<html>Sign in</html>"))
    }

    @Test
    fun `a short row is rejected with its line number`() {
        val text = file("Test Goose\t0.1\t0.2\t")
        assertEquals(Rejected(Reason.TOO_FEW_BINS, line = 8), BarChartParser.parse("XX", text))
    }

    @Test
    fun `a figure that is not a share is rejected`() {
        val notANumber = BarChartParser.parse("XX", file(row("Test Goose") { "n/a" }))
        val overOne = BarChartParser.parse("XX", file(row("Test Goose") { "1.5" }))
        assertEquals(Reason.BAD_NUMBER, (notANumber as Rejected).reason)
        assertEquals(Reason.BAD_NUMBER, (overOne as Rejected).reason)
    }

    @Test
    fun `a file with only non-species has no species`() {
        val result = BarChartParser.parse("XX", file(row("goose sp.") { "0.1" }))
        assertEquals(Rejected(Reason.NO_SPECIES), result)
    }

    @Test
    fun `an empty chart body has no species`() {
        assertTrue(BarChartParser.parse("XX", file()) is Rejected)
    }
}
