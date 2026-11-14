package io.cosgriff.flywaybracket.ui.packs

import androidx.compose.runtime.Immutable
import io.cosgriff.flywaybracket.data.DownloadProgress
import io.cosgriff.flywaybracket.data.FailedDownload
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.ui.components.MeteredAsk
import java.time.LocalDate

/** What the Packs screen shows. A part that is `null` or empty has nothing yet, and the screen says so. */
@Immutable
data class PacksState(
    /** The player's home county. */
    val home: HomeCountyLine? = null,
    /** The pack that holds the home county. It cannot be removed. */
    val homePack: PackRow? = null,
    /** Every other installed pack, in order of state name. */
    val extraPacks: List<PackRow> = emptyList(),
    /** Why the file last chosen was not added; null once a file is added. */
    val problem: PackProblem? = null,
    /** The packs the server has and the phone does not, or why they are not listed (#39). */
    val server: ServerPacks = ServerPacks.Loading,
    /** A large download on a metered connection, waiting for the player's yes. */
    val askMetered: MeteredAsk? = null,
    /** The last download that installed nothing, until its state is tried again. */
    val failedDownload: FailedDownload? = null,
) {
    /** Every installed pack, the home county's first. */
    val packs: List<PackRow> get() = listOfNotNull(homePack) + extraPacks
}

/** Where the list of the server's packs stands. */
sealed interface ServerPacks {
    /** The phone has no account, so the server has not been asked: the consent page comes first. */
    data object NeedsAccount : ServerPacks

    data object Loading : ServerPacks

    data class Failed(val problem: ServerProblem) : ServerPacks

    /**
     * @property available the server's packs that are not on the phone, in order of state name
     * @property onServer how many packs the server has in all, those on the phone included
     */
    @Immutable
    data class Listed(val available: List<ServerPackRow>, val onServer: Int) : ServerPacks
}

/**
 * A pack the server has and the phone does not.
 *
 * @property progress how far its download has come, or null when it is not downloading
 */
@Immutable
data class ServerPackRow(
    val stateCode: String,
    val state: String,
    val sizeBytes: Long,
    val builtOn: LocalDate,
    val progress: DownloadProgress? = null,
)

/**
 * The home county, by its name without "County".
 *
 * @property state the name of its state
 * @property species how many birds are expected there at some time of year, or null when no installed
 *   pack holds the county
 */
@Immutable
data class HomeCountyLine(val county: String, val state: String, val species: Int?)

/**
 * An installed pack. [source] is the pack's attribution line, as its file gives it.
 *
 * @property update the server's other version of the pack, or null when it has the same or none
 */
@Immutable
data class PackRow(
    val stateCode: String,
    val state: String,
    val counties: Int,
    val builtOn: LocalDate,
    val sizeBytes: Long,
    val source: String,
    val update: PackUpdate? = null,
)

/**
 * The server holds another version of an installed pack.
 *
 * @property sizeBytes the size of the server's file
 * @property progress how far its download has come, or null when it is not downloading
 */
@Immutable
data class PackUpdate(val sizeBytes: Long, val progress: DownloadProgress? = null)

/** Why a chosen file was not added. The screen puts each into plain words. */
enum class PackProblem {
    /** The file is something other than a pack. */
    NOT_A_PACK,

    /** A pack file with a part missing or wrong. */
    DAMAGED,

    /** A pack in a later format than this version of the app reads. */
    TOO_NEW,

    /** Larger than any pack is. */
    TOO_LARGE,

    /** The phone could not open the file. */
    NOT_READ,

    /** The pack is good and the phone could not save it. */
    NOT_SAVED,
}
