package io.cosgriff.flywaybracket.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.BuildConfig
import io.cosgriff.flywaybracket.data.AccountRepository
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.data.server.ServerResult
import io.cosgriff.flywaybracket.domain.place.CountyEntry
import io.cosgriff.flywaybracket.domain.place.StateEntry
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Settings (#44): the profile, the account, and what the About section reads from the phone. A saved
 * profile reaches the server through [AccountRepository.syncProfile] when there is an account; with none,
 * nothing is sent.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val accounts: AccountRepository,
    packs: PackRepository,
    counties: BundledCounties,
) : ViewModel() {
    // Compose state, not a flow: a text field needs each keystroke back before the next one.
    var state by mutableStateOf(SettingsState(version = BuildConfig.VERSION_NAME))
        private set

    init {
        viewModelScope.launch {
            val table = counties.table()
            val saved = settings.settings().first()
            val home = saved.homeCountyCode?.let(table::locate)
            state = state.copy(
                profile = ProfileForm(
                    name = saved.displayName.orEmpty(),
                    states = table.states,
                    // With one state in the table there is nothing to choose.
                    state = home?.state ?: table.states.singleOrNull(),
                    county = home?.county,
                    savedName = saved.displayName,
                    savedCounty = saved.homeCountyCode,
                ),
            )
        }
        viewModelScope.launch { accounts.hasAccount().collect { state = state.copy(hasAccount = it) } }
        viewModelScope.launch {
            packs.installed().collect { installed -> state = state.copy(birdData = installed.map { it.pack.source }.distinct()) }
        }
    }

    fun onName(name: String) {
        state = state.copy(profile = state.profile.copy(name = name))
    }

    fun onState(chosen: StateEntry) {
        if (chosen != state.profile.state) state = state.copy(profile = state.profile.copy(state = chosen, county = null))
    }

    fun onCounty(chosen: CountyEntry) {
        state = state.copy(profile = state.profile.copy(county = chosen))
    }

    /** Saves the profile on the phone, then sends it when there is an account. */
    fun onSave() {
        val form = state.profile
        val county = form.county
        if (!form.canSave || county == null) return
        val name = form.name.trim()
        state = state.copy(profile = form.copy(name = name, savedName = name, savedCounty = county.code), profileProblem = null)
        viewModelScope.launch {
            settings.setDisplayName(name)
            settings.setHomeCountyCode(county.code)
            val sent = accounts.syncProfile()
            if (sent is ServerResult.Failed) state = state.copy(profileProblem = sent.problem)
        }
    }

    /** "Delete my account": the first question. */
    fun onDelete() {
        if (state.hasAccount == true && state.delete == DeleteStep.NONE) state = state.copy(delete = DeleteStep.ASKED, deleteProblem = null)
    }

    /** The player read what is deleted and went on: the second question. */
    fun onDeleteContinue() {
        if (state.delete == DeleteStep.ASKED) state = state.copy(delete = DeleteStep.ASKED_AGAIN)
    }

    fun onDeleteCancel() {
        if (state.delete == DeleteStep.ASKED || state.delete == DeleteStep.ASKED_AGAIN) state = state.copy(delete = DeleteStep.NONE)
    }

    /** The player said yes twice: the server deletes the account, then the phone forgets it. */
    fun onDeleteConfirm() {
        if (state.delete != DeleteStep.ASKED_AGAIN) return
        state = state.copy(delete = DeleteStep.DELETING)
        viewModelScope.launch {
            state = when (val deleted = accounts.deleteAccount()) {
                is ServerResult.Ok -> state.copy(delete = DeleteStep.NONE, deleted = true)
                is ServerResult.Failed -> state.copy(delete = DeleteStep.NONE, deleteProblem = deleted.problem)
            }
        }
    }
}
