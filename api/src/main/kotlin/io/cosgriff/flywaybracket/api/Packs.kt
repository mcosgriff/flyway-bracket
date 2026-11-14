package io.cosgriff.flywaybracket.api

import kotlinx.serialization.Serializable

/**
 * One state's pack, as the list of downloads shows it.
 *
 * @property stateCode the eBird region code, such as `US-CO`.
 * @property sizeBytes the size of the pack file.
 * @property builtOn the day the pack was built from its source.
 * @property version changes whenever the pack's content does; the app compares it with the version of
 *   the pack it holds. Not ordered: equal or not is all it says.
 */
@Serializable
data class PackSummary(
    val stateCode: String,
    val name: String,
    val sizeBytes: Long,
    val builtOn: ApiDate,
    val version: String,
)

@Serializable
data class PackListResponse(val packs: List<PackSummary>)
