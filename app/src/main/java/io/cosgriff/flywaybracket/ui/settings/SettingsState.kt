package io.cosgriff.flywaybracket.ui.settings

import androidx.compose.runtime.Immutable
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.domain.place.CountyEntry
import io.cosgriff.flywaybracket.domain.place.StateEntry
import io.cosgriff.flywaybracket.domain.player.DisplayName
import io.cosgriff.flywaybracket.domain.player.DisplayNameCheck

/**
 * What Settings shows (#44).
 *
 * @property hasAccount whether the phone has an account on the league server; null until the store is read
 * @property delete how far the player is through deleting the account
 * @property deleted the account was deleted from this screen
 * @property profileProblem why the profile last saved did not reach the server, or null
 * @property deleteProblem why the last try to delete the account failed, or null
 * @property version the app's version name
 * @property birdData the attribution lines of the installed packs, each once
 */
@Immutable
data class SettingsState(
    val profile: ProfileForm = ProfileForm(),
    val hasAccount: Boolean? = null,
    val delete: DeleteStep = DeleteStep.NONE,
    val deleted: Boolean = false,
    val profileProblem: ServerProblem? = null,
    val deleteProblem: ServerProblem? = null,
    val version: String = "",
    val birdData: List<String> = emptyList(),
)

/** Deleting the account asks twice, then calls the server. */
enum class DeleteStep {
    NONE,

    /** The first question: what is deleted, and what stays. */
    ASKED,

    /** The second: that it cannot be undone. */
    ASKED_AGAIN,

    /** The server has been asked. */
    DELETING,
}

/**
 * The display name and home county as typed and chosen, beside what is saved. The rule is the first run's
 * (#23): [DisplayName].
 *
 * @property savedName the display name saved on the phone, or null
 * @property savedCounty the code of the home county saved on the phone, or null
 */
@Immutable
data class ProfileForm(
    val name: String = "",
    val states: List<StateEntry> = emptyList(),
    val state: StateEntry? = null,
    val county: CountyEntry? = null,
    val savedName: String? = null,
    val savedCounty: String? = null,
) {
    /** Save is on once the name is valid, a county is chosen, and one of the two differs from what is saved. */
    val canSave: Boolean
        get() = DisplayName.check(name) == DisplayNameCheck.VALID &&
            county != null &&
            (name.trim() != savedName || county.code != savedCounty)
}
