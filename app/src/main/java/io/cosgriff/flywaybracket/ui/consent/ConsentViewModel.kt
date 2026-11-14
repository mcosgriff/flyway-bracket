package io.cosgriff.flywaybracket.ui.consent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.AccountRepository
import io.cosgriff.flywaybracket.data.server.ServerResult
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The page League and Bracket show until the player has an account (#38, ADR 0001). Nothing is sent
 * before [onContinue].
 */
@HiltViewModel
class ConsentViewModel @Inject constructor(private val accounts: AccountRepository) : ViewModel() {
    /** Whether there is an account; null until the phone's store has been read. */
    val hasAccount: StateFlow<Boolean?> = accounts.hasAccount().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val mutableState = MutableStateFlow(ConsentState())
    val state: StateFlow<ConsentState> = mutableState.asStateFlow()

    /** The player agreed: ask the server for an account. Also the retry after a failure. */
    fun onContinue() {
        if (mutableState.value.creating) return
        mutableState.value = ConsentState(creating = true)
        viewModelScope.launch {
            mutableState.value = when (val created = accounts.createAccount()) {
                is ServerResult.Ok -> ConsentState()
                is ServerResult.Failed -> ConsentState(problem = created.problem)
            }
        }
    }
}
