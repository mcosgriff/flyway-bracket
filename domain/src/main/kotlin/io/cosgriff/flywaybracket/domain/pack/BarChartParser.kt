package io.cosgriff.flywaybracket.domain.pack

/** What reading a bar chart file gave: the chart, or why there is none. */
sealed interface BarChartParseResult {
    data class Parsed(val chart: CountyChart) : BarChartParseResult

    data class Rejected(val reason: Reason, val line: Int? = null) : BarChartParseResult

    enum class Reason {
        /** No `Sample Size:` row, so this is not a bar chart file. */
        NO_SAMPLE_SIZE_ROW,

        /** A row has fewer than the 48 figures a year needs. */
        TOO_FEW_BINS,

        /** A figure that should be a number is not one, or is outside 0 to 1. */
        BAD_NUMBER,

        /** The file has a sample size row and nothing under it. */
        NO_SPECIES,
    }
}

/**
 * Reads eBird's bar chart download ("Download Histogram Data"): tab-separated text with a
 * `Sample Size:` row of 48 checklist counts, then one row per taxon with 48 frequencies.
 *
 * Only species are kept. Hybrids, "sp." entries, either-or pairs ("Snow/Ross's Goose") and domestic
 * types can't be bracket teams or dropdown choices.
 */
object BarChartParser {
    private const val SAMPLE_SIZE_LABEL = "Sample Size:"

    // Older downloads follow the name with the scientific name in markup; newer ones don't.
    private val scientificNameMarkup = Regex("""\s*\(<em.*$""")
    private val notASpecies = Regex("""\bsp\.|/| x |\(hybrid\)|\(Domestic type\)""")

    fun parse(regionCode: String, text: String): BarChartParseResult {
        val lines = text.lines()
        val sampleRow = lines.indexOfFirst { it.startsWith(SAMPLE_SIZE_LABEL) }
        if (sampleRow < 0) return BarChartParseResult.Rejected(BarChartParseResult.Reason.NO_SAMPLE_SIZE_ROW)

        val checklists = when (val figures = figures(lines[sampleRow], max = Double.MAX_VALUE)) {
            is Figures.Read -> figures.values.map { it.toInt() }
            is Figures.Failed -> return BarChartParseResult.Rejected(figures.reason, sampleRow + 1)
        }

        val species = mutableListOf<SpeciesFrequency>()
        for (i in sampleRow + 1 until lines.size) {
            val line = lines[i]
            if (line.isBlank()) continue
            val name = line.substringBefore('\t').replace(scientificNameMarkup, "").trim()
            when (val figures = figures(line, max = 1.0)) {
                is Figures.Read -> if (!notASpecies.containsMatchIn(name)) species += SpeciesFrequency(name, figures.values)
                is Figures.Failed -> return BarChartParseResult.Rejected(figures.reason, i + 1)
            }
        }
        if (species.isEmpty()) return BarChartParseResult.Rejected(BarChartParseResult.Reason.NO_SPECIES)
        return BarChartParseResult.Parsed(CountyChart(regionCode, checklists, species))
    }

    private sealed interface Figures {
        data class Read(val values: List<Double>) : Figures

        data class Failed(val reason: BarChartParseResult.Reason) : Figures
    }

    /** The 48 figures after a row's label. Rows end with an empty field, which is ignored. */
    private fun figures(line: String, max: Double): Figures {
        val fields = line.split('\t').drop(1).take(TimeBin.COUNT)
        if (fields.size < TimeBin.COUNT) return Figures.Failed(BarChartParseResult.Reason.TOO_FEW_BINS)
        val values = fields.map { field ->
            // toDoubleOrNull reads eBird's scientific notation ("9.16E-4") and no locale's comma.
            field.trim().toDoubleOrNull()?.takeIf { it in 0.0..max }
                ?: return Figures.Failed(BarChartParseResult.Reason.BAD_NUMBER)
        }
        return Figures.Read(values)
    }
}
