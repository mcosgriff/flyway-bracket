package io.cosgriff.flywaybracket.data

import io.cosgriff.flywaybracket.domain.pack.County
import io.cosgriff.flywaybracket.domain.pack.CountyChart
import io.cosgriff.flywaybracket.domain.pack.StatePack
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A state pack on the phone. [sizeBytes] is the size of its file and [version] the file's [packVersion],
 * which the server's list gives for its own copy (#39): the two differ when the server has another.
 */
data class InstalledPack(val pack: StatePack, val sizeBytes: Long, val version: String)

/** A pack file's version as the server names it: the SHA-256 of the file, in lowercase hex (#33). */
fun packVersion(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()

/**
 * The most a pack file is read before it is turned away, whether chosen on the phone or downloaded: a
 * wrong file, a film say, must not be read whole into memory. A state's pack is a few megabytes.
 */
const val MAX_PACK_BYTES = 32 * 1024 * 1024

/** What installing a pack file gave. */
sealed interface PackInstall {
    data class Installed(val pack: InstalledPack) : PackInstall

    /** The file is not a pack this app can read; nothing was saved. */
    data class Rejected(val reason: StatePackResult.Reason) : PackInstall

    /** The pack is good and the phone could not save it. */
    data object NotSaved : PackInstall
}

/**
 * The state packs on the phone (#24): each is its `.fbpack` file, as it arrived, in [directory]. The
 * files are read and parsed once, the first time anything asks; after that every answer comes from
 * memory, and installing or deleting changes the files and the memory together. Made by `PacksModule`.
 *
 * @param directory where the pack files are kept; made when the first pack is installed
 */
class PackRepository(private val directory: File, private val io: CoroutineDispatcher = Dispatchers.IO) {
    // Held while the files are read or changed, so memory and files never part.
    private val mutex = Mutex()

    // Null until the files have been read.
    private val packs = MutableStateFlow<List<InstalledPack>?>(null)

    /** The installed packs, in order of state name, and again after each install or delete. */
    fun installed(): Flow<List<InstalledPack>> = flow {
        loaded()
        emitAll(packs.filterNotNull())
    }

    /**
     * Saves a pack file's [bytes]. They are checked with [StatePackCodec.decode] first, and a file that
     * fails is not saved. A pack for a state that is already installed takes the older one's place, but
     * only once the new one has been checked and written in full: until then the older one stays (#39).
     */
    suspend fun install(bytes: ByteArray): PackInstall = withContext(io) {
        val pack = when (val result = StatePackCodec.decode(bytes)) {
            is StatePackResult.Rejected -> return@withContext PackInstall.Rejected(result.reason)
            is StatePackResult.Read -> result.pack
        }
        // The state's code names the file, so a code that is not one is not written to disk.
        if (!STATE_CODE.matches(pack.state.code)) {
            return@withContext PackInstall.Rejected(StatePackResult.Reason.MISSING_FIELD)
        }
        mutex.withLock {
            val current = packs.value ?: read()
            try {
                write(pack.fileName, bytes)
            } catch (_: IOException) {
                return@withContext PackInstall.NotSaved
            }
            val installed = InstalledPack(pack, bytes.size.toLong(), packVersion(bytes))
            packs.value = (current.filterNot { it.pack.state.code == pack.state.code } + installed).sortedBy { it.pack.state.name }
            PackInstall.Installed(installed)
        }
    }

    /** Removes the pack of the state with this code (`US-CO`), if it is installed. */
    suspend fun delete(stateCode: String): Unit = withContext(io) {
        mutex.withLock {
            val current = packs.value ?: read()
            current.filter { it.pack.state.code == stateCode }.forEach { File(directory, it.pack.fileName).delete() }
            packs.value = current.filterNot { it.pack.state.code == stateCode }
        }
    }

    /** The bar chart of the county with this code (`US-CO-059`), or null when no installed pack holds it. */
    suspend fun chartFor(countyCode: String): CountyChart? =
        loaded().firstNotNullOfOrNull { installed -> installed.pack.counties.firstOrNull { it.code == countyCode } }?.chart

    /** The county this place is in, or null when it is in no county of an installed pack. */
    suspend fun countyAt(longitude: Double, latitude: Double): County? =
        loaded().firstNotNullOfOrNull { it.pack.countyAt(longitude, latitude) }

    private suspend fun loaded(): List<InstalledPack> = packs.value ?: withContext(io) {
        mutex.withLock { packs.value ?: read().also { packs.value = it } }
    }

    /** Every pack file in the directory, parsed. A file that no longer reads as a pack is passed over. */
    private fun read(): List<InstalledPack> =
        directory.listFiles { file -> file.isFile && file.name.endsWith(StatePack.FILE_EXTENSION) }
            .orEmpty()
            .mapNotNull { file ->
                val bytes = try {
                    file.readBytes()
                } catch (_: IOException) {
                    return@mapNotNull null
                }
                (StatePackCodec.decode(bytes) as? StatePackResult.Read)?.let { InstalledPack(it.pack, bytes.size.toLong(), packVersion(bytes)) }
            }
            .sortedBy { it.pack.state.name }

    /** Written beside the file and moved into place, so a write cut short never leaves half a pack. */
    private fun write(fileName: String, bytes: ByteArray) {
        if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Cannot make $directory")
        val partial = File(directory, fileName + PARTIAL_SUFFIX)
        try {
            partial.writeBytes(bytes)
            Files.move(partial.toPath(), File(directory, fileName).toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            partial.delete()
        }
    }

    private companion object {
        /** An eBird state or province code: a country, a hyphen, and up to three letters or digits. */
        val STATE_CODE = Regex("[A-Z]{2}-[A-Z0-9]{1,3}")
        const val PARTIAL_SUFFIX = ".part"
    }
}
