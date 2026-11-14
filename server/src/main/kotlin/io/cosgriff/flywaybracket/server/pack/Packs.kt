package io.cosgriff.flywaybracket.server.pack

import io.cosgriff.flywaybracket.api.PackSummary
import io.cosgriff.flywaybracket.domain.pack.CountyChart
import io.cosgriff.flywaybracket.domain.pack.StatePack
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.io.path.name

/**
 * A pack the server holds: the file's bytes exactly as they are served, and what they decode to.
 *
 * @property version a hash of [bytes]; it changes whenever the file does, and is the download's `ETag`.
 */
class HeldPack(val pack: StatePack, val bytes: ByteArray, val version: String) {
    val summary: PackSummary
        get() = PackSummary(
            stateCode = pack.state.code,
            name = pack.state.name,
            sizeBytes = bytes.size.toLong(),
            builtOn = pack.builtOn,
            version = version,
        )
}

/**
 * What the packs held at one moment name.
 *
 * @property counties the code of every county of every pack, such as `US-CO-059`
 * @property birds the common name of every bird in any county's chart
 */
class PackCatalog(val counties: Set<String>, val birds: Set<String>)

/** A `.fbpack` file in the directory that the server does not hold, and why. */
data class SkippedPack(val fileName: String, val reason: SkipReason)

sealed interface SkipReason {
    /** The codec refused the file. */
    data class Rejected(val reason: StatePackResult.Reason) : SkipReason

    /** The name before `.fbpack` is not a state code, such as `US-CO`. */
    data object NameNotAStateCode : SkipReason

    /** The pack inside is for another state than its file name says. */
    data object StateDoesNotMatchName : SkipReason

    /** The file could not be read. */
    data object Unreadable : SkipReason
}

/**
 * The state packs (#33): every `<state code>.fbpack` file in [directory], which the operator fills
 * (`docs/server/running.md`). The packs are held in memory, the bytes as served and the decoded pack.
 *
 * **When a file changes.** Every question first lists the directory and compares each file's modified time
 * and size with what was read last; only a file that is new or changed is read again, and a file that is
 * gone is dropped. A file is read whole, so a request sees either the old pack or the new one.
 *
 * **Paths.** Nothing a request sends, and nothing inside a pack, is used to make a path. Files are found by
 * listing [directory]; a file whose name is not a state code, or whose pack is for another state, is skipped.
 * A state code from a request is checked against the same pattern and then only looked up in memory.
 *
 * A file the codec refuses is skipped and logged with its reason; the others are still held.
 *
 * @param directory must be a directory when this is made, or the server does not start
 */
class Packs(private val directory: Path, private val log: Logger = LoggerFactory.getLogger(Packs::class.java)) {
    private val lock = Any()

    // Guarded by lock.
    private var files: Map<String, Entry> = emptyMap()

    @Volatile
    private var held: Shelf = Shelf(emptyList())

    init {
        require(Files.isDirectory(directory)) { "The packs directory is not a directory: $directory" }
        refresh()
    }

    /** Every pack held, in order of state name. */
    fun summaries(): List<PackSummary> = shelf().packs.map { it.summary }

    /** The pack of the state with this code (`US-CO`), or null when there is none or the code is not one. */
    fun file(stateCode: String): HeldPack? {
        if (!STATE_CODE.matches(stateCode)) return null
        return shelf().byState[stateCode]
    }

    /** The bar chart of the county with this code (`US-CO-059`), or null when no pack holds it. */
    fun chartFor(countyCode: String): CountyChart? = shelf().charts[countyCode]

    /** The counties and birds of every pack held now, looked at once for a whole batch of sightings. */
    fun catalog(): PackCatalog = shelf().catalog

    /** The `.fbpack` files the last look at the directory did not take, in order of name. */
    fun skipped(): List<SkippedPack> = synchronized(lock) {
        files.values.mapNotNull { (it.result as? Read.Skipped)?.skipped }.sortedBy { it.fileName }
    }

    private fun shelf(): Shelf {
        refresh()
        return held
    }

    /** Reads every pack file that is new or changed since the last look, and drops those that are gone. */
    private fun refresh(): Unit = synchronized(lock) {
        val listed = list() ?: return
        if (listed == files.mapValues { it.value.stamp }) return

        val next = listed.mapValues { (name, stamp) ->
            files[name]?.takeIf { it.stamp == stamp } ?: Entry(stamp, read(name)).also { report(it.result) }
        }
        (files.keys - next.keys).forEach { log.info("Pack file {} is gone", it) }
        files = next
        held = Shelf(next.values.mapNotNull { (it.result as? Read.Held)?.pack }.sortedBy { it.pack.state.name })
    }

    /** Each pack file's name and stamp, or null when the directory cannot be listed. */
    private fun list(): Map<String, Stamp>? = try {
        Files.list(directory).use { paths ->
            paths.toList()
                .filter { it.name.endsWith(StatePack.FILE_EXTENSION) }
                .mapNotNull { path -> stamp(path)?.let { path.name to it } }
                .toMap()
        }
    } catch (failure: IOException) {
        log.warn("Cannot list the packs directory {}; keeping the packs already read", directory, failure)
        null
    }

    private fun stamp(path: Path): Stamp? = try {
        val attributes = Files.readAttributes(path, BasicFileAttributes::class.java)
        if (attributes.isRegularFile) Stamp(attributes.lastModifiedTime(), attributes.size()) else null
    } catch (_: IOException) {
        // Gone between the listing and now.
        null
    }

    private fun read(fileName: String): Read {
        val state = fileName.removeSuffix(StatePack.FILE_EXTENSION)
        if (!STATE_CODE.matches(state)) return skip(fileName, SkipReason.NameNotAStateCode)
        val bytes = try {
            Files.readAllBytes(directory.resolve(fileName))
        } catch (_: IOException) {
            return skip(fileName, SkipReason.Unreadable)
        }
        val pack = when (val result = StatePackCodec.decode(bytes)) {
            is StatePackResult.Rejected -> return skip(fileName, SkipReason.Rejected(result.reason))
            is StatePackResult.Read -> result.pack
        }
        if (pack.state.code != state) return skip(fileName, SkipReason.StateDoesNotMatchName)
        return Read.Held(HeldPack(pack, bytes, version(bytes)))
    }

    private fun skip(fileName: String, reason: SkipReason) = Read.Skipped(SkippedPack(fileName, reason))

    private fun report(result: Read) {
        when (result) {
            is Read.Held -> log.info(
                "Pack {} read: {} counties, built {}, version {}",
                result.pack.pack.state.code,
                result.pack.pack.counties.size,
                result.pack.pack.builtOn,
                result.pack.version,
            )
            is Read.Skipped -> log.warn("Pack file {} skipped: {}", result.skipped.fileName, result.skipped.reason)
        }
    }

    /** What the directory said of a file when it was last read. */
    private data class Stamp(val modified: FileTime, val size: Long)

    private class Entry(val stamp: Stamp, val result: Read)

    private sealed interface Read {
        class Held(val pack: HeldPack) : Read

        class Skipped(val skipped: SkippedPack) : Read
    }

    /** The packs held at one moment, with what requests look up in them. */
    private class Shelf(val packs: List<HeldPack>) {
        val byState: Map<String, HeldPack> = packs.associateBy { it.pack.state.code }

        // A county is in one state's pack; should two packs hold the same code, the first by name wins.
        val charts: Map<String, CountyChart> = packs.flatMap { held -> held.pack.counties.map { it.code to it.chart } }
            .distinctBy { it.first }
            .toMap()

        val catalog = PackCatalog(
            counties = charts.keys,
            birds = charts.values.flatMapTo(HashSet()) { chart -> chart.species.map { it.commonName } },
        )
    }

    companion object {
        /** An eBird state or province code, as the app's `PackRepository` checks it (#24). */
        val STATE_CODE = Regex("[A-Z]{2}-[A-Z0-9]{1,3}")

        /** The hex SHA-256 of a pack file: the version the list gives and the download's `ETag`. */
        fun version(bytes: ByteArray): String =
            HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
    }
}
