package io.cosgriff.flywaybracket.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.domain.outing.OutingLog
import io.cosgriff.flywaybracket.domain.outing.newLifersSince
import io.cosgriff.flywaybracket.domain.outing.outingsSince
import io.cosgriff.flywaybracket.domain.outing.speciesSince
import io.cosgriff.flywaybracket.domain.place.CountyTable
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Home's figures, from the outings saved on the phone (#25), and its region, from the home county in the
 * player's settings (#23). The league and tournament parts stay empty until there is something to read
 * them from.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    outings: OutingRepository,
    settings: SettingsRepository,
    counties: BundledCounties,
    clock: Clock,
) : ViewModel() {
    val state: StateFlow<HomeState> = combine(outings.finishedLogs(), settings.settings()) { logs, player ->
        homeState(
            finished = logs,
            today = LocalDate.now(clock),
            zone = clock.zone,
            region = homeRegion(player.homeCountyCode, counties.table()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), HomeState())

    private companion object {
        /** Long enough to keep the figures across a rotation. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/** How many outings Home lists under "Recent outings". */
internal const val RECENT_OUTINGS = 5

/**
 * The home county as Home's chip names it, or null when none is set or [table] doesn't hold it: until
 * packs are read (#24) the bundled table is the only place a county's name comes from.
 */
internal fun homeRegion(homeCountyCode: String?, table: CountyTable): HomeRegion? =
    homeCountyCode?.let(table::locate)?.let { HomeRegion(county = it.county.name, stateAbbreviation = it.state.abbreviation) }

/**
 * What Home shows for [finished], the player's finished outings, newest first, and [region], the player's
 * home county. With no league, "this season" is the calendar year [today] is in.
 */
internal fun homeState(finished: List<OutingLog>, today: LocalDate, zone: ZoneId, region: HomeRegion? = null): HomeState {
    val seasonStart = today.withDayOfYear(1)
    return HomeState(
        region = region,
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
