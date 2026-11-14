package io.cosgriff.flywaybracket.domain.pack

import io.cosgriff.flywaybracket.domain.pack.StatePackResult.Read
import io.cosgriff.flywaybracket.domain.pack.StatePackResult.Reason
import io.cosgriff.flywaybracket.domain.pack.StatePackResult.Rejected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class StatePackCodecTest {
    private fun gzip(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return bytes.toByteArray()
    }

    private fun gunzip(bytes: ByteArray): String =
        GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)

    private fun figures(figure: String, count: Int = TimeBin.COUNT) = List(count) { figure }.joinToString(",", "[", "]")

    // A file written by hand, with made-up figures, so a test can break one part of it.
    private fun file(
        version: String? = "1",
        builtOn: String = "\"2026-03-14\"",
        checklists: String = figures("250"),
        frequency: String = figures("0.05"),
        outline: String? = "[[[0,0],[1,0],[1,1],[0,1]]]",
        extra: String = "",
    ) = listOfNotNull(
        version?.let { "\"formatVersion\":$it" },
        "\"state\":{\"code\":\"XX-TS\",\"name\":\"Testland\"}",
        "\"builtOn\":$builtOn",
        "\"source\":\"Made-up figures for tests\"",
        "\"counties\":[{" + listOfNotNull(
            "\"code\":\"XX-TS-001\"",
            "\"name\":\"West Square\"",
            "\"checklists\":$checklists",
            "\"species\":[{\"name\":\"Test Warbler\",\"frequency\":$frequency}]",
            outline?.let { "\"outline\":$it" },
        ).joinToString(",") + "}]",
    ).joinToString(",", "{", "$extra}")

    private fun reasonFor(text: String): Reason = (StatePackCodec.decode(gzip(text)) as Rejected).reason

    private fun roundTrip(pack: StatePack): StatePack = (StatePackCodec.decode(StatePackCodec.encode(pack)) as Read).pack

    private fun packOf(vararg species: SpeciesFrequency) = testStatePack().copy(
        counties = listOf(county("XX-TS-001", "West Square", species.toList(), rectangle(0.0, 0.0, 1.0, 1.0))),
    )

    @Test
    fun `encode then decode gives an equal pack`() {
        val pack = testStatePack()
        assertEquals(pack, roundTrip(pack))
    }

    @Test
    fun `the file is gzipped JSON with the format's field names`() {
        val text = gunzip(StatePackCodec.encode(testStatePack()))
        val start =
            "{\"formatVersion\":1,\"state\":{\"code\":\"XX-TS\",\"name\":\"Testland\"},\"builtOn\":\"2026-03-14\"," +
                "\"source\":\"Made-up figures for tests\",\"counties\":[{\"code\":\"XX-TS-001\",\"name\":\"West Square\"," +
                "\"checklists\":[400,"
        val end =
            "\"outline\":[[[3.0,0.0],[4.0,0.0],[4.0,1.0],[3.0,1.0]],[[5.0,0.0],[6.0,0.0],[6.0,1.0],[5.0,1.0]]]}]}"
        assertEquals(start, text.take(start.length))
        assertEquals(end, text.takeLast(end.length))
    }

    @Test
    fun `reads a file written by hand`() {
        val pack = (StatePackCodec.decode(gzip(file())) as Read).pack
        val county = pack.counties.single()
        assertEquals(PackState("XX-TS", "Testland"), pack.state)
        assertEquals("XX-TS-001", county.code)
        assertEquals(250, county.chart.checklists.last())
        assertEquals(0.05, county.chart.find("Test Warbler")!!.at(TimeBin(17)), 0.0)
        assertEquals(county, pack.countyAt(0.5, 0.5))
    }

    @Test
    fun `frequencies are stored rounded to four decimal places`() {
        val read = roundTrip(packOf(resident("Test Sparrow", 0.123449), resident("Test Wren", 0.98765)))
        val chart = read.counties.single().chart
        assertEquals(0.1234, chart.find("Test Sparrow")!!.at(TimeBin(0)), 0.0)
        assertEquals(0.9877, chart.find("Test Wren")!!.at(TimeBin(0)), 0.0)
    }

    @Test
    fun `a species under a tenth of a percent in every bin is left out`() {
        val read = roundTrip(packOf(resident("Test Sparrow", 0.5), resident("Test Rarity", 0.00099)))
        val chart = read.counties.single().chart
        assertEquals(listOf("Test Sparrow"), chart.species.map { it.commonName })
        assertNull(chart.find("Test Rarity"))
    }

    @Test
    fun `a species at a tenth of a percent in one bin is kept`() {
        val oneWeek = seasonal("Test Flyover", TimeBin(17), TimeBin(17), inSeason = 0.001)
        val read = roundTrip(packOf(oneWeek))
        assertEquals(oneWeek, read.counties.single().chart.find("Test Flyover"))
    }

    @Test
    fun `a rare species is left out only of the county where it is rare`() {
        val pack = testStatePack()
        val rareInWest = pack.counties[0].let { it.copy(chart = it.chart.copy(species = it.chart.species + resident("Test Tern", 0.0002))) }
        val read = roundTrip(pack.copy(counties = listOf(rareInWest) + pack.counties.drop(1)))
        assertNull(read.counties[0].chart.find("Test Tern"))
        assertEquals(pack.counties[2].chart.find("Test Tern"), read.counties[2].chart.find("Test Tern"))
    }

    @Test
    fun `a field the reader does not know is passed over`() {
        assertEquals(StatePackCodec.decode(gzip(file())), StatePackCodec.decode(gzip(file(extra = ",\"licence\":\"none\""))))
    }

    @Test
    fun `bytes that are not gzip are rejected`() {
        assertEquals(Rejected(Reason.NOT_GZIP), StatePackCodec.decode(file().toByteArray()))
        assertEquals(Rejected(Reason.NOT_GZIP), StatePackCodec.decode(ByteArray(0)))
    }

    @Test
    fun `a gzip cut short is rejected`() {
        val bytes = StatePackCodec.encode(testStatePack())
        assertEquals(Rejected(Reason.NOT_GZIP), StatePackCodec.decode(bytes.copyOf(bytes.size / 2)))
    }

    @Test
    fun `gzip that does not hold a JSON object is rejected`() {
        assertEquals(Reason.NOT_JSON, reasonFor("Test Warbler\t0.25"))
        assertEquals(Reason.NOT_JSON, reasonFor(file().dropLast(1)))
        assertEquals(Reason.NOT_JSON, reasonFor("[1,2,3]"))
        assertEquals(Reason.NOT_JSON, reasonFor(""))
    }

    @Test
    fun `a missing field is rejected`() {
        assertEquals(Reason.MISSING_FIELD, reasonFor(file(outline = null)))
        assertEquals(Reason.MISSING_FIELD, reasonFor(file(version = null)))
        assertEquals(Reason.MISSING_FIELD, reasonFor("{\"formatVersion\":1}"))
    }

    @Test
    fun `a field of the wrong kind is rejected as missing`() {
        assertEquals(Reason.MISSING_FIELD, reasonFor(file(version = "\"1\"")))
        assertEquals(Reason.MISSING_FIELD, reasonFor(file(builtOn = "\"14 March 2026\"")))
        assertEquals(Reason.MISSING_FIELD, reasonFor(file(checklists = "\"many\"")))
    }

    @Test
    fun `an outline that is not rings of pairs is rejected as missing`() {
        assertEquals(Reason.MISSING_FIELD, reasonFor(file(outline = "[]")))
        assertEquals(Reason.MISSING_FIELD, reasonFor(file(outline = "[[[0,0],[1,0]]]")))
        assertEquals(Reason.MISSING_FIELD, reasonFor(file(outline = "[[[0,0],[1,0],[1]]]")))
        assertEquals(Reason.MISSING_FIELD, reasonFor(file(outline = "[[[0,0,5],[1,0,5],[1,1,5]]]")))
    }

    @Test
    fun `a later format version is too new, whatever else the file holds`() {
        assertEquals(Reason.TOO_NEW, reasonFor(file(version = "2")))
        assertEquals(Reason.TOO_NEW, reasonFor("{\"formatVersion\":7,\"regions\":[]}"))
    }

    @Test
    fun `a row without 48 figures is a bad bin count`() {
        assertEquals(Reason.BAD_BIN_COUNT, reasonFor(file(checklists = figures("250", count = 47))))
        assertEquals(Reason.BAD_BIN_COUNT, reasonFor(file(frequency = figures("0.05", count = 49))))
        assertEquals(Reason.BAD_BIN_COUNT, reasonFor(file(frequency = "[]")))
    }
}
