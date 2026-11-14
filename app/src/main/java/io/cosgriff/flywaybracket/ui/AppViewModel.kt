package io.cosgriff.flywaybracket.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.AccountRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.data.sync.SightingSync
import io.cosgriff.flywaybracket.data.sync.SyncScheduler
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the whole app needs to know before it can show a screen. Kept by the activity. */
@HiltViewModel
class AppViewModel @Inject constructor(
    settings: SettingsRepository,
    accounts: AccountRepository,
    sync: SightingSync,
    scheduler: SyncScheduler,
) : ViewModel() {
    /**
     * Whether the first-run screen is due: the display name or the home county is missing (#23). `null`
     * until the settings have been read.
     */
    val needsOnboarding: StateFlow<Boolean?> = settings.settings()
        .map { !it.isComplete }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        // A changed display name or home county reaches the server while the app is open. With no
        // account this sends nothing (#38).
        viewModelScope.launch { accounts.keepProfileInSync() }
        // The app was opened: the league, bracket and results are fetched and the queue sent, once online
        // (#43). With no account there is nothing to fetch.
        viewModelScope.launch { if (accounts.hasAccount().first()) scheduler.syncNow() }
        // Sightings due to be sent, such as those of an outing just finished, go once the phone is online.
        viewModelScope.launch {
            sync.queueState().map { it.due }.distinctUntilChanged().collect { due -> if (due > 0) scheduler.sendSoon() }
        }
    }
}
