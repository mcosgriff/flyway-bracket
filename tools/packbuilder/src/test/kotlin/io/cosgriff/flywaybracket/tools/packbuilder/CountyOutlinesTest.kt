package io.cosgriff.flywaybracket.tools.packbuilder

import io.cosgriff.flywaybracket.domain.pack.LonLat
import io.cosgriff.flywaybracket.tools.packbuilder.CountyOutlinesResult.Read
import io.cosgriff.flywaybracket.tools.packbuilder.CountyOutlinesResult.Reason
import io.cosgriff.flywaybracket.tools.packbuilder.CountyOutlinesResult.Rejected
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Made-up counties with square outlines.
class CountyOutlinesTest {
    private val square = "[[[1.0, 2.0], [3.0, 2.0], [3.0, 4.0], [1.0, 4.0], [1.0, 2.0]]]"

    private fun feature(
        properties: String = """{ "STATEFP": "08", "COUNTYFP": "001", "NAME": "Alder" }""",
        geometry: String = """{ "type": "Polygon", "coordinates": $square }""",
    ) = """{ "type": "Feature", "properties": $properties, "geometry": $geometry }"""

    private fun collection(vararg features: String, name: String? = "cb_2023_us_county_20m") =
        """{ "type": "FeatureCollection", ${name?.let { "\"name\": \"$it\", " }.orEmpty()}"features": [${features.joinToString()}] }"""

    private fun parse(text: String) = CountyOutlines.parse("08", text)

    @Test
    fun `reads a county's code, name and corners`() {
        val county = (parse(collection(feature())) as Read).counties.single()

        assertEquals("001", county.fips)
        assertEquals("Alder", county.name)
        assertEquals(LonLat(1.0, 2.0), county.outline.rings.single().first())
        assertEquals(5, county.outline.rings.single().size)
    }

    @Test
    fun `reads the vintage from the census file's name`() {
        assertEquals(2023, (parse(collection(feature())) as Read).vintage)
        assertEquals(2019, (parse(collection(feature(), name = "cb_2019_08_county_20m")) as Read).vintage)
        assertNull((parse(collection(feature(), name = "counties")) as Read).vintage)
        assertNull((parse(collection(feature(), name = null)) as Read).vintage)
    }

    @Test
    fun `a position's height is passed over`() {
        val withHeight = "[[[1.0, 2.0, 9.0], [3.0, 2.0, 9.0], [3.0, 4.0, 9.0], [1.0, 2.0, 9.0]]]"
        val county = (parse(collection(feature(geometry = """{ "type": "Polygon", "coordinates": $withHeight }"""))) as Read)
            .counties.single()

        assertEquals(LonLat(3.0, 4.0), county.outline.rings.single()[2])
    }

    @Test
    fun `text that is not a feature collection is rejected`() {
        listOf("", "not json", "[]", """{ "type": "Feature" }""", """{ "features": {} }""").forEach {
            assertEquals(it, Rejected(Reason.NOT_GEOJSON), parse(it))
        }
        assertEquals(Rejected(Reason.NOT_GEOJSON, feature = 2), parse(collection(feature(), "7")))
    }

    @Test
    fun `a feature without the census properties is rejected by its place in the file`() {
        val noState = feature(properties = """{ "COUNTYFP": "003", "NAME": "Birch" }""")
        val noCounty = feature(properties = """{ "STATEFP": "08", "NAME": "Birch" }""")
        val noName = feature(properties = """{ "STATEFP": "08", "COUNTYFP": "003" }""")
        val numberCode = feature(properties = """{ "STATEFP": "08", "COUNTYFP": 3, "NAME": "Birch" }""")

        listOf(noState, noCounty, noName, numberCode, feature(properties = "null")).forEach {
            assertEquals(it, Rejected(Reason.MISSING_PROPERTY, feature = 2), parse(collection(feature(), it)))
        }
    }

    @Test
    fun `a geometry that is not polygons is rejected`() {
        val point = """{ "type": "Point", "coordinates": [1.0, 2.0] }"""
        val twoCorners = """{ "type": "Polygon", "coordinates": [[[1.0, 2.0], [3.0, 2.0]]] }"""
        val words = """{ "type": "Polygon", "coordinates": [[["a", "b"], ["c", "d"], ["e", "f"]]] }"""
        val oneFigure = """{ "type": "Polygon", "coordinates": [[[1.0], [3.0], [3.0]]] }"""
        val noRings = """{ "type": "Polygon", "coordinates": [] }"""
        val flatMulti = """{ "type": "MultiPolygon", "coordinates": $square }"""

        listOf(point, twoCorners, words, oneFigure, noRings, flatMulti, "null").forEach {
            assertEquals(it, Rejected(Reason.BAD_GEOMETRY, feature = 1), parse(collection(feature(geometry = it))))
        }
    }

    @Test
    fun `another state's feature is passed over, whatever its geometry`() {
        val elsewhere = feature(properties = """{ "STATEFP": "56" }""", geometry = "null")

        assertEquals(1, (parse(collection(elsewhere, feature())) as Read).counties.size)
    }
}
