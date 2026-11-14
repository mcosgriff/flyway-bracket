package io.cosgriff.flywaybracket.tools.packbuilder

import io.cosgriff.flywaybracket.domain.pack.BarChartParseResult
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import io.cosgriff.flywaybracket.domain.pack.TimeBin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText

// Every input is made up (src/test/resources/made-up-inputs): no figure comes from eBird.
class PackBuilderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var packsLocal: Path
    private lateinit var barCharts: Path
    private lateinit var outlines: Path

    private val today = LocalDate.of(2026, 11, 13)
    private val alderChart get() = barCharts.resolve("ebird_US-CO-001__1900_2026_1_12_barchart.txt")
    private val birchChart get() = barCharts.resolve("ebird_US-CO-003__1900_2026_1_12_barchart.txt")

    @Before
    fun copyInputs() {
        packsLocal = temp.root.toPath().resolve("packs-local")
        val from = Path.of(checkNotNull(javaClass.getResource("/made-up-inputs/US-CO")).toURI())
        Files.walk(from).use { files ->
            files.forEach { it.copyTo(packsLocal.resolve("US-CO").resolve(from.relativize(it).toString()).createParents()) }
        }
        barCharts = packsLocal.resolve("US-CO/barcharts")
        outlines = packsLocal.resolve("US-CO/counties.geojson")
        downloadedOn(alderChart, LocalDate.of(2026, 11, 2))
        downloadedOn(birchChart, LocalDate.of(2026, 11, 5))
    }

    private fun Path.createParents(): Path = also { it.parent.createDirectories() }

    private fun downloadedOn(file: Path, day: LocalDate) {
        Files.setLastModifiedTime(file, FileTime.from(day.atTime(12, 0).toInstant(ZoneOffset.UTC)))
    }

    private fun build(code: String = "US-CO", censusVintage: Int? = null) =
        PackBuilder(packsLocal, ZoneOffset.UTC).build(code, today, censusVintage)

    private fun built() = build() as PackBuildResult.Built

    @Test
    fun `builds a pack of the counties that have a bar chart and an outline`() {
        val pack = built().pack

        assertEquals("US-CO", pack.state.code)
        assertEquals("Colorado", pack.state.name)
        assertEquals(today, pack.builtOn)
        assertEquals(listOf("US-CO-001" to "Alder", "US-CO-003" to "Birch"), pack.counties.map { it.code to it.name })
        assertEquals(0.25, pack.counties[0].chart.find("Test Warbler")!!.at(TimeBin(16)), 0.0)
        assertEquals(500, pack.counties[1].chart.checklists.first())
    }

    @Test
    fun `source names the newest download day and the census vintage`() {
        assertEquals(
            "eBird bar chart data, downloaded 2026-11-05; " +
                "US Census Bureau cartographic boundary file, 2023, 1:20,000,000",
            built().pack.source,
        )
    }

    @Test
    fun `the bytes are a pack file that reads back`() {
        val result = built()
        val read = (StatePackCodec.decode(result.bytes) as StatePackResult.Read).pack

        assertEquals(result.pack.source, read.source)
        assertEquals(listOf("US-CO-001", "US-CO-003"), read.counties.map { it.code })
    }

    @Test
    fun `counts the species the file holds`() {
        // Alder: the either-or pair is not a species, and the pipit is under the file's floor all year.
        assertEquals(
            listOf(CountyRef("US-CO-001", "Alder") to 2, CountyRef("US-CO-003", "Birch") to 3),
            built().speciesByCounty,
        )
    }

    @Test
    fun `a polygon gives its outer ring and a multipolygon one ring a piece`() {
        val (alder, birch) = built().pack.counties

        assertEquals(1, alder.outline.rings.size)
        // Inside the hole the file drew: version 1 outlines have no holes.
        assertTrue(alder.outline.contains(-104.5, 39.5))
        assertEquals(2, birch.outline.rings.size)
        assertTrue(birch.outline.contains(-102.25, 39.25))
        assertFalse(birch.outline.contains(-104.5, 39.5))
    }

    @Test
    fun `another state's counties in the outlines file are passed over`() {
        val result = built()

        assertEquals(emptyList<CountyRef>(), result.missingBarChart)
        assertEquals(emptyList<CountyRef>(), result.missingOutline)
    }

    @Test
    fun `a county with no bar chart is listed and left out`() {
        birchChart.deleteExisting()

        val result = built()

        assertEquals(listOf("US-CO-001"), result.pack.counties.map { it.code })
        assertEquals(listOf(CountyRef("US-CO-003", "Birch")), result.missingBarChart)
        assertEquals("eBird bar chart data, downloaded 2026-11-02", result.pack.source.substringBefore(';'))
    }

    @Test
    fun `a county with no outline is listed and left out`() {
        alderChart.copyTo(barCharts.resolve("ebird_US-CO-059__1900_2026_1_12_barchart.txt"))

        val result = built()

        assertEquals(listOf("US-CO-001", "US-CO-003"), result.pack.counties.map { it.code })
        assertEquals(listOf(CountyRef("US-CO-059", null)), result.missingOutline)
    }

    @Test
    fun `files that are not this state's bar charts are passed over`() {
        alderChart.copyTo(barCharts.resolve("ebird_US-WY-001__1900_2026_1_12_barchart.txt"))
        barCharts.resolve("notes.txt").writeText("not a bar chart")

        assertEquals(emptyList<CountyRef>(), built().missingOutline)
    }

    @Test
    fun `a bar chart the parser rejects stops the build with its file and reason`() {
        birchChart.writeText(birchChart.readText().replace("0.75", "many"))

        val result = build() as PackBuildResult.BadBarChart

        assertEquals(birchChart.name, result.file.name)
        assertEquals(BarChartParseResult.Reason.BAD_NUMBER, result.reason)
        assertEquals(9, result.line)
    }

    @Test
    fun `two bar charts for one county stop the build`() {
        val second = barCharts.resolve("ebird_US-CO-001__2016_2026_1_12_barchart.txt")
        alderChart.copyTo(second)

        val result = build() as PackBuildResult.DuplicateBarChart

        assertEquals("US-CO-001", result.countyCode)
        assertEquals(listOf(alderChart.name, second.name), result.files.map { it.name })
    }

    @Test
    fun `a code that is not a state's is refused`() {
        assertEquals(PackBuildResult.UnknownState("US-ZZ"), build("US-ZZ"))
        assertEquals(PackBuildResult.UnknownState("CO"), build("CO"))
    }

    @Test
    fun `a state with no outlines file is refused`() {
        assertEquals(PackBuildResult.NoOutlinesFile(packsLocal.resolve("US-WY/counties.geojson")), build("US-WY"))
    }

    @Test
    fun `an outlines file that is not geojson is refused`() {
        outlines.writeText("STATEFP,COUNTYFP")

        val result = build() as PackBuildResult.BadOutlinesFile

        assertEquals(CountyOutlinesResult.Reason.NOT_GEOJSON, result.reason)
        assertNull(result.feature)
    }

    @Test
    fun `no county with both inputs is refused`() {
        alderChart.deleteExisting()
        birchChart.deleteExisting()

        val result = build() as PackBuildResult.NoCounties

        assertEquals(listOf("US-CO-001", "US-CO-003"), result.missingBarChart.map { it.code })
    }

    @Test
    fun `an outlines file that does not name its census file needs the vintage given`() {
        outlines.writeText(outlines.readText().replace("cb_2023_us_county_20m", "counties"))

        assertEquals(PackBuildResult.NoCensusVintage(outlines), build())
        val source = (build(censusVintage = 2022) as PackBuildResult.Built).pack.source
        assertTrue(source, source.endsWith("cartographic boundary file, 2022, 1:20,000,000"))
    }

    // The command line.

    private class Run(val exitCode: Int, val out: String, val err: String)

    private fun run(vararg args: String): Run {
        val out = ByteArrayOutputStream()
        val err = ByteArrayOutputStream()
        val exitCode = run(args.toList(), packsLocal, today, PrintStream(out), PrintStream(err))
        return Run(exitCode, out.toString(), err.toString())
    }

    @Test
    fun `the command writes the pack and prints a summary`() {
        barCharts.resolve("ebird_US-CO-059__1900_2026_1_12_barchart.txt").let { alderChart.copyTo(it) }

        val run = run("US-CO")

        val file = packsLocal.resolve("out/US-CO.fbpack")
        assertEquals(run.err, 0, run.exitCode)
        assertTrue(StatePackCodec.decode(file.readBytes()) is StatePackResult.Read)
        val lines = run.out.lines()
        assertTrue(run.out, "File size: ${Files.size(file)} bytes" in lines)
        assertTrue(run.out, "Counties found: 2" in lines)
        assertTrue(run.out, "  US-CO-001 Alder: 2 species" in lines)
        assertTrue(run.out, "  US-CO-003 Birch: 3 species" in lines)
        assertTrue(run.out, "Counties missing a bar chart: none" in lines)
        assertTrue(run.out, "Counties missing an outline: 1" in lines)
        assertTrue(run.out, "  US-CO-059" in lines)
    }

    @Test
    fun `the command names a rejected bar chart and writes nothing`() {
        alderChart.writeText("not a bar chart")

        val run = run("US-CO")

        assertEquals(1, run.exitCode)
        assertEquals("${alderChart.name} was rejected: NO_SAMPLE_SIZE_ROW", run.err.trim())
        assertFalse(packsLocal.resolve("out").exists())
    }

    @Test
    fun `the command takes the census vintage as an option`() {
        outlines.writeText(outlines.readText().replace("cb_2023_us_county_20m", "counties"))

        assertEquals(1, run("US-CO").exitCode)
        assertEquals(0, run("US-CO", "--census-vintage=2022").exitCode)
    }

    @Test
    fun `the command shows its usage for arguments it does not know`() {
        listOf(emptyArray(), arrayOf("US-CO", "US-WY"), arrayOf("US-CO", "--fast"), arrayOf("US-CO", "--census-vintage=new"))
            .forEach { args ->
                val run = run(*args)
                assertEquals(2, run.exitCode)
                assertTrue(run.err, run.err.startsWith("Usage:"))
            }
    }
}
