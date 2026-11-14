package io.cosgriff.flywaybracket.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.data.PackRepository
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Home's figures, from the outings saved on the phone (#25), and its region, from the home county in the
 * player's settings (#23). Start outing starts one in the home county, or continues the one in progress
 * (#26). The league and tournament parts stay empty until there is something to read them from.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val outings: OutingRepository,
    private val settings: SettingsRepository,
    counties: BundledCounties,
    private val packs: PackRepository,
    clock: Clock,
) : ViewModel() {
    private val packMissing = MutableStateFlow(false)
    private val opened = Channel<Unit>(Channel.BUFFERED)

    val state: StateFlow<HomeState> = combine(
        outings.finishedLogs(),
        settings.settings(),
        outings.inProgress(),
        packMissing,
    ) { logs, player, inProgress, missing ->
        homeState(
            finished = logs,
            today = LocalDate.now(clock),
            zone = clock.zone,
            region = homeRegion(player.homeCountyCode, counties.table()),
        ).copy(outingInProgress = inProgress != null, packMissing = missing)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), HomeState())

    /** Emits once each time the Outing screen should open, with an outing in progress. */
    val openOuting: Flow<Unit> = opened.receiveAsFlow()

    /**
     * Opens the outing in progress, or starts one in the home county now. With no pack holding the home
     * county, nothing starts and Home says why.
     */
    fun onStartOuting() {
        viewModelScope.launch {
            if (startOrContinue()) opened.send(Unit) else packMissing.value = true
        }
    }

    fun onPackMissingDismissed() {
        packMissing.value = false
    }

    /** Whether an outing is now in progress. */
    private suspend fun startOrContinue(): Boolean {
        if (outings.inProgress().first() != null) return true
        val home = settings.settings().first().homeCountyCode ?: return false
        if (packs.chartFor(home) == null) return false
        // No place name yet: the player gives one on the Outing screen if they like.
        outings.start(placeName = "", countyCode = home)
        return true
    }

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
