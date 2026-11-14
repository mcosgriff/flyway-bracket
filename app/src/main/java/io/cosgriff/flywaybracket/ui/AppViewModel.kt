package io.cosgriff.flywaybracket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.SettingsRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** What the whole app needs to know before it can show a screen. Kept by the activity. */
@HiltViewModel
class AppViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    /**
     * Whether the first-run screen is due: the display name or the home county is missing (#23). `null`
     * until the settings have been read.
     */
    val needsOnboarding: StateFlow<Boolean?> = settings.settings()
        .map { !it.isComplete }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
