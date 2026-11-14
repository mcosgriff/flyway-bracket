package io.cosgriff.flywaybracket.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.domain.outing.OutingLog
import io.cosgriff.flywaybracket.domain.outing.newLifersSince
import io.cosgriff.flywaybracket.domain.outing.outingsSince
import io.cosgriff.flywaybracket.domain.outing.speciesSince
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Home's figures, from the outings saved on the phone (#25). The league, tournament and region parts stay
 * empty until there is something to read them from.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(outings: OutingRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<HomeState> = outings.finishedLogs()
        .map { logs -> homeState(logs, today = LocalDate.now(clock), zone = clock.zone) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), HomeState())

    private companion object {
        /** Long enough to keep the figures across a rotation. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/** How many outings Home lists under "Recent outings". */
internal const val RECENT_OUTINGS = 5

/**
 * What Home shows for [finished], the player's finished outings, newest first. With no league, "this season"
 * is the calendar year [today] is in.
 */
internal fun homeState(finished: List<OutingLog>, today: LocalDate, zone: ZoneId): HomeState {
    val seasonStart = today.withDayOfYear(1)
    return HomeState(
        season = SeasonStats(
            species = speciesSince(finished, seasonStart, zone).size,
            lifers = newLifersSince(finished, seasonStart, zone).size,
            outings = outingsSince(finished, seasonStart, zone),
        ),
        recentOutings = finished.take(RECENT_OUTINGS).map { log ->
            OutingRow(
                id = log.outing.id,
                place = log.outing.placeName,
                date = log.outing.date(zone),
                length = Duration.between(log.outing.startedAt, log.outing.endedAt ?: log.outing.startedAt),
                species = log.species.size,
            )
        },
    )
}
