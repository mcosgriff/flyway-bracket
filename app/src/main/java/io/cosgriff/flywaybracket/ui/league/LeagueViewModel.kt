package io.cosgriff.flywaybracket.ui.league

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.League
import io.cosgriff.flywaybracket.data.LeagueRepository
import io.cosgriff.flywaybracket.data.server.ServerActivity
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.data.server.ServerResult
import io.cosgriff.flywaybracket.domain.league.LeaguePhase
import io.cosgriff.flywaybracket.ui.components.message
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The League tab (#40): the player's league as the phone holds it, brought up to date when the tab opens; or,
 * with none, the Join view. Only shown once the phone has an account (#38).
 */
@HiltViewModel
class LeagueViewModel @Inject constructor(
    private val leagues: LeagueRepository,
    private val clock: Clock,
    activity: ServerActivity,
) : ViewModel() {
    /** Whether the server is most likely waking while the tab waits on it (#93). */
    val waking: StateFlow<Boolean> = activity.waking

    /** Where reading the league from the server stands. */
    private sealed interface Refresh {
        data object Running : Refresh

        data object Done : Refresh

        data class Failed(val problem: ServerProblem) : Refresh
    }

    /** Leaving: asked, being sent, or why it failed. */
    private data class Leave(val asked: Boolean = false, val leaving: Boolean = false, val problem: ServerProblem? = null)

    private val refresh = MutableStateFlow<Refresh>(Refresh.Running)
    private val join = MutableStateFlow(JoinState())
    private val joinAgain = MutableStateFlow(false)
    private val leave = MutableStateFlow(Leave())

    val state: StateFlow<LeagueTabState> = combine(leagues.league(), refresh, join, joinAgain, leave) { league, refresh, join, again, leave ->
        tabState(league, refresh, join, again, leave)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), LeagueTabState.Loading)

    init {
        refresh()
    }

    /** Reads the league from the server. Also the retry after a failure. */
    fun refresh() {
        refresh.value = Refresh.Running
        viewModelScope.launch {
            refresh.value = when (val read = leagues.refresh()) {
                is ServerResult.Ok -> Refresh.Done
                is ServerResult.Failed -> Refresh.Failed(read.problem)
            }
        }
    }

    fun onCode(code: String) {
        join.update { it.copy(code = code, problem = null) }
    }

    fun onJoin() = enter(JoinBusy.JOINING) { leagues.join(join.value.code) }

    fun onFindLeague() = enter(JoinBusy.FINDING) { leagues.findLeague() }

    /** From a finished or closed league: the Join view, which Back leaves. */
    fun onJoinAgain() {
        joinAgain.value = true
    }

    fun onBackFromJoin() {
        joinAgain.value = false
        join.update { it.copy(problem = null) }
    }

    fun onLeave() {
        leave.value = Leave(asked = true)
    }

    fun onCancelLeave() {
        leave.value = Leave()
    }

    fun onConfirmLeave() {
        if (leave.value.leaving) return
        leave.value = Leave(leaving = true)
        viewModelScope.launch {
            leave.value = when (val left = leagues.leave()) {
                is ServerResult.Ok -> Leave()
                is ServerResult.Failed -> Leave(problem = left.problem)
            }
        }
    }

    private fun enter(busy: JoinBusy, call: suspend () -> ServerResult<League>) {
        if (join.value.busy != null) return
        join.update { it.copy(busy = busy, problem = null) }
        viewModelScope.launch {
            when (val entered = call()) {
                is ServerResult.Ok -> {
                    join.value = JoinState()
                    joinAgain.value = false
                    refresh.value = Refresh.Done
                }
                is ServerResult.Failed -> join.update { it.copy(busy = null, problem = entered.problem.message) }
            }
        }
    }

    private fun tabState(league: League?, refresh: Refresh, join: JoinState, again: Boolean, leave: Leave): LeagueTabState = when {
        league == null -> when (refresh) {
            Refresh.Running -> LeagueTabState.Loading
            is Refresh.Failed -> LeagueTabState.Failed(refresh.problem.message)
            Refresh.Done -> LeagueTabState.Join(join)
        }
        // A finished or closed league leaves the player free to join another.
        again && league.phase in FREE -> LeagueTabState.Join(join.copy(canGoBack = true))
        else -> LeagueTabState.InLeague(
            leagueView(league, clock.instant(), clock.zone).copy(
                problem = leave.problem?.message ?: (refresh as? Refresh.Failed)?.problem?.message,
                confirmLeave = leave.asked,
                leaving = leave.leaving,
            ),
        )
    }

    private companion object {
        /** Long enough to keep the league across a rotation. */
        const val STOP_AFTER_MILLIS = 5_000L

        val FREE = setOf(LeaguePhase.FINISHED, LeaguePhase.CLOSED)
    }
}
