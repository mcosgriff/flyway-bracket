package io.cosgriff.flywaybracket.ui.packs

import androidx.compose.runtime.Immutable
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
) {
    /** Every installed pack, the home county's first. */
    val packs: List<PackRow> get() = listOfNotNull(homePack) + extraPacks
}

/**
 * The home county, by its name without "County".
 *
 * @property state the name of its state
 * @property species how many birds are expected there at some time of year, or null when no installed
 *   pack holds the county
 */
@Immutable
data class HomeCountyLine(val county: String, val state: String, val species: Int?)

/** An installed pack. [source] is the pack's attribution line, as its file gives it. */
@Immutable
data class PackRow(
    val stateCode: String,
    val state: String,
    val counties: Int,
    val builtOn: LocalDate,
    val sizeBytes: Long,
    val source: String,
)

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
