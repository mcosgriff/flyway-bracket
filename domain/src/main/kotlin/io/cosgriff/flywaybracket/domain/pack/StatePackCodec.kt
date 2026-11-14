package io.cosgriff.flywaybracket.domain.pack

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.roundToLong

/** What reading a state pack file gave: the pack, or why there is none. */
sealed interface StatePackResult {
    data class Read(val pack: StatePack) : StatePackResult

    data class Rejected(val reason: Reason) : StatePackResult

    enum class Reason {
        /** The bytes are not gzip, or the gzip is cut short. */
        NOT_GZIP,

        /** What was unzipped is not a JSON object. */
        NOT_JSON,

        /** A field the format needs is absent, or holds something of the wrong kind. */
        MISSING_FIELD,

        /** The file's `formatVersion` is later than this reader knows. */
        TOO_NEW,

        /** A county's checklists, or a species' frequencies, are not the 48 figures a year needs. */
        BAD_BIN_COUNT,
    }
}

/**
 * Writes and reads a state pack file: JSON, then gzip.
 *
 * The JSON has `formatVersion`, `state` (`code`, `name`), `builtOn` (ISO date), `source` and
 * `counties`. A county has `code`, `name`, `checklists` (48 counts), `species` (each a `name` and 48
 * `frequency` figures) and `outline` (rings of `[longitude, latitude]` pairs).
 */
object StatePackCodec {
    /** The version [encode] writes, and the latest [decode] reads. */
    const val FORMAT_VERSION = 1

    /** 0.1% of checklists. A species under this in every bin of a county is left out of that county. */
    const val KEPT_FLOOR = 0.001

    // Four decimal places.
    private const val FREQUENCY_SCALE = 10_000.0

    private const val VERSION_FIELD = "formatVersion"

    // A later file of the same version may add a field; this reader passes over it.
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The pack as file bytes. Frequencies are rounded to four decimal places, and a species under
     * [KEPT_FLOOR] in every bin of a county is left out of that county, so [decode] gives back an
     * equal pack only when the pack was already so.
     */
    fun encode(pack: StatePack): ByteArray {
        val text = json.encodeToString(PackFile.serializer(), pack.toFile())
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(bytes).use { it.write(text.toByteArray(Charsets.UTF_8)) }
        return bytes.toByteArray()
    }

    fun decode(bytes: ByteArray): StatePackResult {
        val text = try {
            GZIPInputStream(bytes.inputStream()).use { it.readBytes() }.toString(Charsets.UTF_8)
        } catch (_: IOException) {
            return rejected(StatePackResult.Reason.NOT_GZIP)
        }
        val root = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return rejected(StatePackResult.Reason.NOT_JSON)

        // The version is read on its own first: a later version may have moved every other field.
        val version = version(root[VERSION_FIELD]) ?: return rejected(StatePackResult.Reason.MISSING_FIELD)
        if (version > FORMAT_VERSION) return rejected(StatePackResult.Reason.TOO_NEW)

        val file = try {
            json.decodeFromJsonElement(PackFile.serializer(), root)
        } catch (_: SerializationException) {
            return rejected(StatePackResult.Reason.MISSING_FIELD)
        }
        return file.toPack()
    }

    private fun rejected(reason: StatePackResult.Reason) = StatePackResult.Rejected(reason)

    private fun version(field: JsonElement?): Int? = (field as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull

    private fun round(frequency: Double): Double = (frequency * FREQUENCY_SCALE).roundToLong() / FREQUENCY_SCALE

    private fun StatePack.toFile() = PackFile(
        formatVersion = FORMAT_VERSION,
        state = StateEntry(state.code, state.name),
        builtOn = builtOn.toString(),
        source = source,
        counties = counties.map { county ->
            CountyEntry(
                code = county.code,
                name = county.name,
                checklists = county.chart.checklists,
                species = county.chart.species
                    .filter { species -> species.frequency.any { it >= KEPT_FLOOR } }
                    .map { SpeciesEntry(it.commonName, it.frequency.map(::round)) },
                outline = county.outline.rings.map { ring -> ring.map { listOf(it.longitude, it.latitude) } },
            )
        },
    )

    private fun PackFile.toPack(): StatePackResult {
        val built = try {
            LocalDate.parse(builtOn)
        } catch (_: DateTimeParseException) {
            return rejected(StatePackResult.Reason.MISSING_FIELD)
        }
        val read = counties.map { entry ->
            val binCounts = entry.species.map { it.frequency.size } + entry.checklists.size
            if (binCounts.any { it != TimeBin.COUNT }) return rejected(StatePackResult.Reason.BAD_BIN_COUNT)

            val allPairs = entry.outline.all { ring -> ring.all { it.size == 2 } }
            if (!allPairs) return rejected(StatePackResult.Reason.MISSING_FIELD)
            val rings = entry.outline.map { ring -> ring.map { (longitude, latitude) -> LonLat(longitude, latitude) } }
            if (!Outline.isValid(rings)) return rejected(StatePackResult.Reason.MISSING_FIELD)

            val species = entry.species.map { SpeciesFrequency(it.name, it.frequency) }
            County(entry.name, CountyChart(entry.code, entry.checklists, species), Outline(rings))
        }
        return StatePackResult.Read(StatePack(PackState(state.code, state.name), built, source, read))
    }

    // The file's shape, kept apart from the domain classes so that renaming a property of theirs never
    // changes the file.

    @Serializable
    private data class PackFile(
        val formatVersion: Int,
        val state: StateEntry,
        val builtOn: String,
        val source: String,
        val counties: List<CountyEntry>,
    )

    @Serializable
    private data class StateEntry(val code: String, val name: String)

    @Serializable
    private data class CountyEntry(
        val code: String,
        val name: String,
        val checklists: List<Int>,
        val species: List<SpeciesEntry>,
        val outline: List<List<List<Double>>>,
    )

    @Serializable
    private data class SpeciesEntry(val name: String, val frequency: List<Double>)
}
