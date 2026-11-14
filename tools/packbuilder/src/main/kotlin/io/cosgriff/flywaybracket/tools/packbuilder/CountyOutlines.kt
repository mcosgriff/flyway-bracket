package io.cosgriff.flywaybracket.tools.packbuilder

import io.cosgriff.flywaybracket.domain.pack.LonLat
import io.cosgriff.flywaybracket.domain.pack.Outline
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

/**
 * One county's outline from the Census file.
 *
 * @param fips the county's three-digit code within its state, such as `059`
 */
data class CountyOutline(val fips: String, val name: String, val outline: Outline)

/** What reading the county outlines file gave: the state's counties, or why there are none. */
sealed interface CountyOutlinesResult {
    /** @param vintage the year of the Census file, when the GeoJSON still carries the file's name */
    data class Read(val vintage: Int?, val counties: List<CountyOutline>) : CountyOutlinesResult

    /** @param feature which feature of the file is at fault, counted from 1; null for the file itself */
    data class Rejected(val reason: Reason, val feature: Int? = null) : CountyOutlinesResult

    enum class Reason {
        /** Not JSON, or not a GeoJSON `FeatureCollection` with `features`. */
        NOT_GEOJSON,

        /** A feature has no `STATEFP`, or one of this state's has no `COUNTYFP` or `NAME`. */
        MISSING_PROPERTY,

        /** A county's geometry is not a `Polygon` or `MultiPolygon` of `[longitude, latitude]` rings. */
        BAD_GEOMETRY,
    }
}

/**
 * Reads county outlines from a Census Bureau cartographic boundary file converted to GeoJSON: a
 * `FeatureCollection` whose features carry `STATEFP`, `COUNTYFP` and `NAME` and a `Polygon` or
 * `MultiPolygon`.
 *
 * Only a polygon's outer ring is kept: a pack's outline has no holes.
 */
object CountyOutlines {
    // ogr2ogr names the collection after the file it converted, such as `cb_2023_us_county_20m`.
    private val boundaryFileName = Regex("""cb_(\d{4})_""")

    /** The counties of the state with this FIPS code; the file may hold other states' too. */
    fun parse(stateFips: String, text: String): CountyOutlinesResult {
        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        }
        val features = root?.get("features") as? JsonArray ?: return rejected(CountyOutlinesResult.Reason.NOT_GEOJSON)

        val counties = mutableListOf<CountyOutline>()
        features.forEachIndexed { index, element ->
            val number = index + 1
            val feature = element as? JsonObject ?: return rejected(CountyOutlinesResult.Reason.NOT_GEOJSON, number)
            val properties = feature["properties"] as? JsonObject
            val state = properties.text("STATEFP")
                ?: return rejected(CountyOutlinesResult.Reason.MISSING_PROPERTY, number)
            if (state != stateFips) return@forEachIndexed

            val fips = properties.text("COUNTYFP")
            val name = properties.text("NAME")
            if (fips == null || name == null) return rejected(CountyOutlinesResult.Reason.MISSING_PROPERTY, number)
            val rings = outerRings(feature["geometry"] as? JsonObject)?.takeIf(Outline::isValid)
                ?: return rejected(CountyOutlinesResult.Reason.BAD_GEOMETRY, number)
            counties += CountyOutline(fips, name, Outline(rings))
        }
        val vintage = root.text("name")?.let { boundaryFileName.find(it) }?.groupValues?.get(1)?.toInt()
        return CountyOutlinesResult.Read(vintage, counties)
    }

    private fun rejected(reason: CountyOutlinesResult.Reason, feature: Int? = null) =
        CountyOutlinesResult.Rejected(reason, feature)

    private fun JsonObject?.text(key: String): String? =
        (this?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content

    /** The outer ring of each polygon of the geometry, or null when it is not polygons. */
    private fun outerRings(geometry: JsonObject?): List<List<LonLat>>? {
        val coordinates = geometry?.get("coordinates") as? JsonArray ?: return null
        val polygons = when (geometry.text("type")) {
            "Polygon" -> listOf(coordinates)
            "MultiPolygon" -> coordinates
            else -> return null
        }
        return polygons.map { polygon ->
            val outer = (polygon as? JsonArray)?.firstOrNull() as? JsonArray ?: return null
            outer.map { corner(it) ?: return null }
        }
    }

    // A position may carry a third figure, the height; it is passed over.
    private fun corner(position: JsonElement): LonLat? {
        val figures = (position as? JsonArray)?.map { (it as? JsonPrimitive)?.doubleOrNull ?: return null }
        if (figures == null || figures.size < 2) return null
        return LonLat(figures[0], figures[1])
    }
}
