package io.cosgriff.flywaybracket.ui.onboarding

import androidx.compose.runtime.Immutable
import io.cosgriff.flywaybracket.domain.place.CountyEntry
import io.cosgriff.flywaybracket.domain.place.StateEntry
import io.cosgriff.flywaybracket.domain.player.DisplayName
import io.cosgriff.flywaybracket.domain.player.DisplayNameCheck

/** What the first-run screen shows: the name as typed, the states to choose from, and what is chosen. */
@Immutable
data class OnboardingState(
    val name: String = "",
    /** The states of the bundled table; empty until it has been read. */
    val states: List<StateEntry> = emptyList(),
    val state: StateEntry? = null,
    val county: CountyEntry? = null,
) {
    val nameCheck: DisplayNameCheck get() = DisplayName.check(name)

    /** Continue is on once the name is valid and a home county is chosen. */
    val canContinue: Boolean get() = nameCheck == DisplayNameCheck.VALID && county != null
}
