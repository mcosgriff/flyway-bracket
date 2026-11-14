package io.cosgriff.flywaybracket.ui.onboarding

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.domain.place.CountyEntry
import io.cosgriff.flywaybracket.domain.place.StateEntry
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The first-run form. It starts from what is already saved, so a player who has one of the two is asked
 * only for the other. Saving both ends the first run: the navigation host watches the settings and moves
 * on to Home.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val settings: SettingsRepository,
    counties: BundledCounties,
) : ViewModel() {
    // Compose state, not a flow: a text field needs each keystroke back before the next one.
    var state by mutableStateOf(OnboardingState())
        private set

    init {
        viewModelScope.launch {
            val table = counties.table()
            val saved = settings.settings().first()
            val home = saved.homeCountyCode?.let(table::locate)
            state = state.copy(
                name = state.name.ifEmpty { saved.displayName.orEmpty() },
                states = table.states,
                // With one state in the table there is nothing to choose.
                state = home?.state ?: table.states.singleOrNull(),
                county = home?.county,
            )
        }
    }

    fun onName(name: String) {
        state = state.copy(name = name)
    }

    fun onState(chosen: StateEntry) {
        if (chosen != state.state) state = state.copy(state = chosen, county = null)
    }

    fun onCounty(chosen: CountyEntry) {
        state = state.copy(county = chosen)
    }

    fun onContinue() {
        val form = state
        val county = form.county
        if (!form.canContinue || county == null) return
        viewModelScope.launch {
            settings.setDisplayName(form.name)
            settings.setHomeCountyCode(county.code)
        }
    }
}
