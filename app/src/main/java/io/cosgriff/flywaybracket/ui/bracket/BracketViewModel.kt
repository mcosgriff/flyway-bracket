package io.cosgriff.flywaybracket.ui.bracket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.R
import io.cosgriff.flywaybracket.api.ApiErrorCode
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.data.AccountRepository
import io.cosgriff.flywaybracket.data.BracketRepository
import io.cosgriff.flywaybracket.data.LeagueBracket
import io.cosgriff.flywaybracket.data.PickResult
import io.cosgriff.flywaybracket.data.PicksNotice
import io.cosgriff.flywaybracket.data.server.ServerActivity
import io.cosgriff.flywaybracket.data.server.ServerProblem
import io.cosgriff.flywaybracket.data.server.ServerResult
import io.cosgriff.flywaybracket.domain.bracket.BracketPicks
import io.cosgriff.flywaybracket.domain.bracket.DrawPosition
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Team
import io.cosgriff.flywaybracket.ui.components.message
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Bracket tab (#42): finds the player's league from their profile, shows its bracket as the phone holds
 * it, brings it up to date, and saves the player's picks. Only shown once the phone has an account (#38).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BracketViewModel @Inject constructor(
    private val accounts: AccountRepository,
    private val brackets: BracketRepository,
    private val clock: Clock,
    activity: ServerActivity,
) : ViewModel() {
    /** Whether the server is most likely waking while the tab waits on it (#93). */
    val waking: StateFlow<Boolean> = activity.waking

    /** Where finding the league stands. */
    private sealed interface Lookup {
        data object Loading : Lookup

        data object NoLeague : Lookup

        data class Failed(val problem: ServerProblem) : Lookup

        /** @property notReady the server has no bracket for the league yet */
        data class Found(val leagueId: String, val notReady: Boolean = false) : Lookup
    }

    private val lookup = MutableStateFlow<Lookup>(Lookup.Loading)
    private val problem = MutableStateFlow<ServerProblem?>(null)
    private val pickProblem = MutableStateFlow<Int?>(null)
    private val confirm = MutableStateFlow<PickChange?>(null)

    val state: StateFlow<BracketScreenState> = lookup.flatMapLatest { found ->
        when (found) {
            Lookup.Loading -> flowOf(BracketScreenState.Loading)
            Lookup.NoLeague -> flowOf(BracketScreenState.NoLeague(forming = false))
            is Lookup.Failed -> flowOf(BracketScreenState.Failed(found.problem.message))
            is Lookup.Found -> combine(brackets.bracket(found.leagueId), problem, pickProblem, confirm) { bracket, problem, pickProblem, confirm ->
                when {
                    bracket != null -> BracketScreenState.Ready(
                        bracketState(bracket, clock.instant(), clock.zone).copy(problem = pickProblem ?: problem?.message, confirm = confirm),
                    )
                    found.notReady -> BracketScreenState.NoLeague(forming = true)
                    problem != null -> BracketScreenState.Failed(problem.message)
                    else -> BracketScreenState.Loading
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), BracketScreenState.Loading)

    init {
        load()
    }

    /** Reads the player's league, then brings its bracket up to date. Also the retry after a failure. */
    fun load() {
        viewModelScope.launch {
            val current = lookup.value
            if (current is Lookup.Found) {
                refresh(current.leagueId)
                return@launch
            }
            lookup.value = Lookup.Loading
            lookup.value = when (val profile = accounts.profile()) {
                is ServerResult.Failed -> Lookup.Failed(profile.problem)
                is ServerResult.Ok -> {
                    val league = profile.value.league
                    // A league that closed never started: it has no bracket to show.
                    if (league == null || league.state == LeagueState.CLOSED) Lookup.NoLeague else Lookup.Found(league.id)
                }
            }
            (lookup.value as? Lookup.Found)?.let { refresh(it.leagueId) }
        }
    }

    private suspend fun refresh(leagueId: String) {
        val refreshed = brackets.refresh(leagueId)
        val failed = (refreshed as? ServerResult.Failed)?.problem
        problem.value = failed
        lookup.value = Lookup.Found(leagueId, notReady = failed == ServerProblem.Refused(ApiErrorCode.BRACKET_NOT_READY))
    }

    /**
     * The player tapped [bird] in the matchup at [at]. A pick that clears later picks waits for
     * [onConfirmPick]; any other is saved at once.
     */
    fun onPick(at: DrawPosition, bird: Team) {
        val bracket = (state.value as? BracketScreenState.Ready)?.bracket ?: return
        if (bracket.locked) return
        val picks = bracket.view.pickedBirds
        if (picks[at] == bird.commonName) return
        val cleared = BracketPicks.clearedBy(picks, at, bird.commonName)
            .sortedWith(compareBy({ it.round.ordinal }, { it.position }))
            .map { ClearedPick(it.round, picks.getValue(it)) }
        if (cleared.isEmpty()) save(at, bird) else confirm.value = PickChange(at, bird, cleared)
    }

    fun onConfirmPick() {
        val change = confirm.value ?: return
        confirm.value = null
        save(change.at, change.bird)
    }

    fun onCancelPick() {
        confirm.value = null
    }

    fun onDismissNotice() {
        val leagueId = (lookup.value as? Lookup.Found)?.leagueId ?: return
        viewModelScope.launch { brackets.dismissNotice(leagueId) }
    }

    /** The league whose bracket is shown, for the Matchup page; null until it is found. */
    val leagueId: String? get() = (lookup.value as? Lookup.Found)?.leagueId

    private fun save(at: DrawPosition, bird: Team) {
        val leagueId = (lookup.value as? Lookup.Found)?.leagueId ?: return
        viewModelScope.launch {
            // A pick saved on the phone but not sent is said by the bracket's picksUnsent, not as a problem.
            pickProblem.value = when (brackets.pick(leagueId, at, bird.commonName)) {
                is PickResult.Saved -> null
                PickResult.Locked -> R.string.bracket_problem_locked
                is PickResult.DoesNotFit -> R.string.bracket_problem_does_not_fit
                PickResult.NoBracket -> {
                    refresh(leagueId)
                    null
                }
            }
        }
    }

    private companion object {
        /** Long enough to keep the bracket across a rotation. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/** What the Bracket tab shows for [bracket] at [now]; the lock time is given in [phoneZone]. */
internal fun bracketState(bracket: LeagueBracket, now: Instant, phoneZone: ZoneId): BracketState {
    val calendar = bracket.calendar
    val locked = bracket.picksLocked(now)
    return BracketState(
        view = bracket.view,
        season = calendar.season,
        locked = locked,
        locksAt = calendar.picksLockOn.atStartOfDay(bracket.timeZone).withZoneSameInstant(phoneZone).toLocalDateTime(),
        previewUntil = calendar.fieldFixedOn.takeIf { bracket.preview },
        liveRound = bracket.roundOn(now)?.takeIf { locked },
        roundEnds = Round.entries.associateWith { calendar.roundDates(it).endInclusive },
        picksUnsent = bracket.picksUnsent,
        notice = when (bracket.notice) {
            PicksNotice.LOCKED_BEFORE_SENT -> BracketNotice.LOCKED_BEFORE_SENT
            PicksNotice.BRACKET_CHANGED -> BracketNotice.BRACKET_CHANGED
            null -> null
        },
    )
}
