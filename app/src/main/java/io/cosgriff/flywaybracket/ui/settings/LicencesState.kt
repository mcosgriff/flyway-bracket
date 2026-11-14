package io.cosgriff.flywaybracket.ui.settings

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What the Licences screen shows (#44).
 *
 * @property fontLicence the full text of the bundled font's licence, or null until read
 * @property libraries the open-source libraries in the app, or null until read
 * @property notRead the list of libraries could not be read
 */
@Immutable
data class LicencesState(
    val fontLicence: String? = null,
    val libraries: List<LibraryLicence>? = null,
    val notRead: Boolean = false,
)

/**
 * A library in the app, as its Maven POM gives it. The build writes one for each library in the release
 * APK to the asset [LIBRARIES_ASSET] (`libraryLicences` in `app/build.gradle.kts`).
 *
 * @property coordinates its group and name: `androidx.core:core`
 */
@Immutable
@Serializable
data class LibraryLicence(val name: String, val coordinates: String, val version: String, val licences: List<Licence>)

/** A licence by its name in the POM, and where its text is, when the POM says. */
@Immutable
@Serializable
data class Licence(val name: String, val url: String? = null)

/** Written by the build; never edited by hand. */
const val LIBRARIES_ASSET = "licences/libraries.json"

/** The OFL text of Inter, the bundled font. */
const val FONT_LICENCE_ASSET = "licences/inter-OFL.txt"

private val json = Json { ignoreUnknownKeys = true }

/** The libraries in the build's list, or null when [text] is not one. */
fun parseLibraries(text: String): List<LibraryLicence>? = try {
    json.decodeFromString<List<LibraryLicence>>(text)
} catch (_: SerializationException) {
    null
} catch (_: IllegalArgumentException) {
    null
}
