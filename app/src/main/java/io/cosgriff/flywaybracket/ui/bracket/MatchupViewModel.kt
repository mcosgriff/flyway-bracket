package io.cosgriff.flywaybracket.ui.bracket

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.BracketRepository
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.domain.bracket.DrawPosition
import io.cosgriff.flywaybracket.domain.bracket.Field
import io.cosgriff.flywaybracket.domain.bracket.PlayerRound
import io.cosgriff.flywaybracket.domain.outing.OutingLog
import io.cosgriff.flywaybracket.domain.outing.RoundSightings
import java.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

/**
 * One matchup of the player's bracket (#42): both birds and how the league logged them, from the bracket on the
 * phone; and the player's own part, from their outings and packs on the phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MatchupViewModel.Factory::class)
class MatchupViewModel @AssistedInject constructor(
    @Assisted leagueId: String,
    @Assisted at: DrawPosition,
    brackets: BracketRepository,
    settings: SettingsRepository,
    packs: PackRepository,
    outings: OutingRepository,
    clock: Clock,
) : ViewModel() {
    /** Null until the bracket is read, and if the phone holds none. */
    val state: StateFlow<MatchupState?> = combine(brackets.bracket(leagueId), settings.settings(), logs(outings)) { bracket, player, logs ->
        bracket ?: return@combine null
        val dates = bracket.calendar.roundDates(at.round)
        val today = clock.instant().atZone(bracket.timeZone).toLocalDate()
        // Before the round starts the player has no part in it yet.
        val round = if (today < dates.start) {
            null
        } else {
            val sightings = RoundSightings.of(logs, dates, clock.zone)
            PlayerRound(
                home = player.homeCountyCode?.let { packs.chartFor(it) },
                visited = sightings.counties.mapNotNull { packs.chartFor(it) },
                logged = sightings.birds,
            )
        }
        matchupState(
            matchup = bracket.view.matchup(at),
            quarter = Field.quarterOf(at),
            dates = dates,
            live = bracket.roundOn(clock.instant()) == at.round,
            bins = bracket.calendar.bins(at.round),
            player = round,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    @AssistedFactory
    interface Factory {
        fun create(leagueId: String, at: DrawPosition): MatchupViewModel
    }

    private companion object {
        /** Long enough to keep the page across a rotation. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/** Every outing on the phone with its sightings: the finished ones and the one in progress. */
@OptIn(ExperimentalCoroutinesApi::class)
private fun logs(outings: OutingRepository): Flow<List<OutingLog>> =
    combine(outings.finishedLogs(), outings.inProgress().flatMapLatest { it?.let { outing -> outings.log(outing.id) } ?: flowOf(null) }) { finished, current ->
        finished + listOfNotNull(current)
    }
