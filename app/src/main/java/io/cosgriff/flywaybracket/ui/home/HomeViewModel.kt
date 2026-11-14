package io.cosgriff.flywaybracket.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Home's state. Only the home county is stored so far (#23); the rest stays empty until outings and
 * leagues exist. A home county the bundled table doesn't hold shows no chip.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(settings: SettingsRepository, counties: BundledCounties) : ViewModel() {
    val state: StateFlow<HomeState> = settings.settings()
        .map { player ->
            val home = player.homeCountyCode?.let { counties.table().locate(it) }
            HomeState(region = home?.let { HomeRegion(county = it.county.name, stateAbbreviation = it.state.abbreviation) })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), HomeState())

    private companion object {
        // Long enough to keep the flow through a rotation.
        const val STOP_AFTER_MILLIS = 5_000L
    }
}
