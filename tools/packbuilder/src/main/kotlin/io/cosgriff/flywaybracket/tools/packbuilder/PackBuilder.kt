package io.cosgriff.flywaybracket.tools.packbuilder

import io.cosgriff.flywaybracket.domain.pack.BarChartParseResult
import io.cosgriff.flywaybracket.domain.pack.BarChartParser
import io.cosgriff.flywaybracket.domain.pack.County
import io.cosgriff.flywaybracket.domain.pack.CountyChart
import io.cosgriff.flywaybracket.domain.pack.PackState
import io.cosgriff.flywaybracket.domain.pack.StatePack
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.time.ZoneId
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

/** A county, by its eBird region code (`US-CO-059`) and, where the outlines file gave one, its name. */
data class CountyRef(val code: String, val name: String?)

/** What building a pack gave: the pack and its file bytes, or why there is none. */
sealed interface PackBuildResult {
    /**
     * @param bytes the pack as its file
     * @param speciesByCounty how many species each county has in the file, in the pack's county order
     * @param missingBarChart counties with an outline and no bar chart file
     * @param missingOutline counties with a bar chart file and no outline
     */
    class Built(
        val pack: StatePack,
        val bytes: ByteArray,
        val speciesByCounty: List<Pair<CountyRef, Int>>,
        val missingBarChart: List<CountyRef>,
        val missingOutline: List<CountyRef>,
    ) : PackBuildResult

    sealed interface Failed : PackBuildResult

    /** The code is not `US-` and a state's two letters. */
    data class UnknownState(val code: String) : Failed

    data class NoOutlinesFile(val file: Path) : Failed

    data class BadOutlinesFile(val file: Path, val reason: CountyOutlinesResult.Reason, val feature: Int?) : Failed

    /** The outlines file does not say which year's Census file it is, and no year was given. */
    data class NoCensusVintage(val file: Path) : Failed

    data class BadBarChart(val file: Path, val reason: BarChartParseResult.Reason, val line: Int?) : Failed

    /** Two bar chart files are for one county. */
    data class DuplicateBarChart(val countyCode: String, val files: List<Path>) : Failed

    /** No county has both a bar chart and an outline, so there is nothing to put in a pack. */
    data class NoCounties(val missingBarChart: List<CountyRef>, val missingOutline: List<CountyRef>) : Failed

    /** The file this tool built does not read back. A fault in the tool or the codec. */
    data class Unreadable(val reason: StatePackResult.Reason) : Failed
}

/**
 * Builds a state pack from `<packs-local>/<state>/barcharts/` (eBird bar chart downloads, one per
 * county) and `<packs-local>/<state>/counties.geojson` (Census county outlines). A county is in the
 * pack only when it has both.
 *
 * @param zone the time zone a bar chart file's modified time is read in, for the day it was downloaded
 */
class PackBuilder(private val packsLocal: Path, private val zone: ZoneId = ZoneId.systemDefault()) {

    /**
     * @param stateCode the eBird region code, such as `US-CO`
     * @param today the pack's `builtOn`
     * @param censusVintage the year of the Census file, for an outlines file that does not name it
     */
    fun build(stateCode: String, today: LocalDate, censusVintage: Int? = null): PackBuildResult {
        val state = UsState.ofRegionCode(stateCode) ?: return PackBuildResult.UnknownState(stateCode)
        val stateDir = packsLocal.resolve(state.regionCode)

        val outlinesFile = stateDir.resolve(OUTLINES_FILE)
        if (!outlinesFile.exists()) return PackBuildResult.NoOutlinesFile(outlinesFile)
        val outlines = when (val read = CountyOutlines.parse(state.fips, outlinesFile.readText())) {
            is CountyOutlinesResult.Read -> read
            is CountyOutlinesResult.Rejected ->
                return PackBuildResult.BadOutlinesFile(outlinesFile, read.reason, read.feature)
        }
        val vintage = censusVintage ?: outlines.vintage ?: return PackBuildResult.NoCensusVintage(outlinesFile)

        val chartFiles = barChartFiles(state, stateDir.resolve(BAR_CHARTS_DIR))
        chartFiles.entries.firstOrNull { it.value.size > 1 }?.let {
            return PackBuildResult.DuplicateBarChart(it.key, it.value)
        }
        val charts = chartFiles.mapValues { (code, files) ->
            val file = files.single()
            when (val parsed = BarChartParser.parse(code, file.readText())) {
                is BarChartParseResult.Parsed -> ParsedChart(file, parsed.chart)
                is BarChartParseResult.Rejected -> return PackBuildResult.BadBarChart(file, parsed.reason, parsed.line)
            }
        }

        // An eBird county code ends with the county's three-digit FIPS code.
        val outlineByCode = outlines.counties.associateBy { "${state.regionCode}-${it.fips}" }
        val missingBarChart = outlineByCode
            .filterKeys { it !in charts }
            .map { CountyRef(it.key, it.value.name) }
            .sortedBy { it.code }
        val missingOutline = charts.keys.filter { it !in outlineByCode }.map { CountyRef(it, null) }
        val counties = charts.mapNotNull { (code, parsed) ->
            outlineByCode[code]?.let { County(it.name, parsed.chart, it.outline) }
        }
        if (counties.isEmpty()) return PackBuildResult.NoCounties(missingBarChart, missingOutline)

        val downloaded = counties.maxOf { modifiedOn(charts.getValue(it.code).file) }
        val pack = StatePack(PackState(state.regionCode, state.name), today, source(downloaded, vintage), counties)
        val bytes = StatePackCodec.encode(pack)
        // Read back: proof the file is one the app can read, and the species it really holds.
        val written = when (val read = StatePackCodec.decode(bytes)) {
            is StatePackResult.Read -> read.pack
            is StatePackResult.Rejected -> return PackBuildResult.Unreadable(read.reason)
        }
        return PackBuildResult.Built(
            pack = pack,
            bytes = bytes,
            speciesByCounty = written.counties.map { CountyRef(it.code, it.name) to it.chart.species.size },
            missingBarChart = missingBarChart,
            missingOutline = missingOutline,
        )
    }

    private data class ParsedChart(val file: Path, val chart: CountyChart)

    /** The bar chart files of this state, by county code, in code order. */
    private fun barChartFiles(state: UsState, dir: Path): Map<String, List<Path>> {
        if (!dir.isDirectory()) return emptyMap()
        val fileName = Regex("""ebird_(${Regex.escape(state.regionCode)}-\d{3})__.*_barchart\.txt""")
        return dir.listDirectoryEntries()
            .sortedBy { it.name }
            .mapNotNull { file -> fileName.matchEntire(file.name)?.let { it.groupValues[1] to file } }
            .groupBy({ it.first }, { it.second })
            .toSortedMap()
    }

    private fun modifiedOn(file: Path): LocalDate =
        Files.getLastModifiedTime(file).toInstant().atZone(zone).toLocalDate()

    companion object {
        const val OUTLINES_FILE = "counties.geojson"
        const val BAR_CHARTS_DIR = "barcharts"

        /**
         * A pack's `source`: where its figures and its outlines came from. Dates are ISO.
         *
         * @param downloaded the day the newest bar chart file in the pack was downloaded
         */
        fun source(downloaded: LocalDate, censusVintage: Int): String =
            "eBird bar chart data, downloaded $downloaded; " +
                "US Census Bureau cartographic boundary file, $censusVintage, 1:20,000,000"
    }
}
