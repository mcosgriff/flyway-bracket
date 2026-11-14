package io.cosgriff.flywaybracket.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.api.PackSummary
import io.cosgriff.flywaybracket.data.AccountRepository
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.CountyFix
import io.cosgriff.flywaybracket.data.CountyLocator
import io.cosgriff.flywaybracket.data.Downloads
import io.cosgriff.flywaybracket.data.NetworkMeter
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.data.PackDownloads
import io.cosgriff.flywaybracket.data.PackListing
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.data.asksBeforeDownload
import io.cosgriff.flywaybracket.domain.outing.OutingLog
import io.cosgriff.flywaybracket.domain.outing.newLifersSince
import io.cosgriff.flywaybracket.domain.outing.outingsSince
import io.cosgriff.flywaybracket.domain.outing.speciesSince
import io.cosgriff.flywaybracket.domain.place.CountyTable
import io.cosgriff.flywaybracket.ui.components.MeteredAsk
import io.cosgriff.flywaybracket.ui.outing.CountyFallback
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Home's figures, from the outings saved on the phone (#25), and its region, from the home county in the
 * player's settings (#23). Start outing continues the outing in progress, or starts one (#26) in the
 * county the phone is in, found from its approximate location, else in the home county (#27). With an
 * account, when the server has the pack of the home county's state and the phone does not, Home offers to
 * download it (#39). The league and tournament parts stay empty until there is something to read them from.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val outings: OutingRepository,
    private val settings: SettingsRepository,
    counties: BundledCounties,
    private val packs: PackRepository,
    private val locator: CountyLocator,
    clock: Clock,
    private val downloads: PackDownloads,
    accounts: AccountRepository,
    private val meter: NetworkMeter,
) : ViewModel() {
    private val starting = MutableStateFlow(Starting())
    private val opened = Channel<OpenOuting>(Channel.BUFFERED)

    // The server's pack of the home county's state, while the phone does not have it (#39).
    private val offered = MutableStateFlow<PackSummary?>(null)
    private val askMetered = MutableStateFlow<MeteredAsk?>(null)

    val state: StateFlow<HomeState> = combine(
        combine(
            outings.finishedLogs(),
            settings.settings(),
            outings.inProgress(),
            starting,
        ) { logs, player, inProgress, start ->
            homeState(
                finished = logs,
                today = LocalDate.now(clock),
                zone = clock.zone,
                region = homeRegion(player.homeCountyCode, counties.table()),
            ).copy(
                outingInProgress = inProgress != null,
                packMissing = start.packMissing,
                askLocation = start.askLocation,
                findingCounty = start.findingCounty,
            )
        },
        offered,
        downloads.state,
        askMetered,
    ) { home, offer, running, ask ->
        home.copy(packOffer = offer?.let { packOffer(it, running) }, askMetered = ask)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), HomeState())

    init {
        // Asked only with an account, and only while the home county's state has no pack on the phone. Once
        // the pack is installed, the offer goes.
        viewModelScope.launch {
            combine(settings.settings(), packs.installed(), accounts.hasAccount()) { player, installed, has ->
                val state = player.homeCountyCode?.let(::stateCodeOf)
                state?.takeIf { has && installed.none { it.pack.state.code == state } }
            }.distinctUntilChanged().collectLatest { state ->
                offered.value = null
                if (state != null) offered.value = (downloads.available() as? PackListing.Listed)?.packs?.firstOrNull { it.stateCode == state }
            }
        }
    }

    /** Downloads the offered pack. Over 5 MB on a metered connection, it asks first. */
    fun onDownloadPack() {
        val pack = offered.value ?: return
        if (asksBeforeDownload(pack.sizeBytes, meter.isMetered())) {
            askMetered.value = MeteredAsk(pack.stateCode, pack.name, pack.sizeBytes)
        } else {
            downloads.start(pack)
        }
    }

    fun onMeteredConfirmed() {
        askMetered.value = null
        offered.value?.let(downloads::start)
    }

    fun onMeteredDismissed() {
        askMetered.value = null
    }

    /** Emits once each time the Outing screen should open, with an outing in progress. */
    val openOuting: Flow<OpenOuting> = opened.receiveAsFlow()

    /**
     * Opens the outing in progress, or starts one. The first time, while the app may not use the phone's
     * location, Home first shows the page that explains why it would; the answer comes to
     * [onLocationAnswered]. With no county found and no pack holding the home county, nothing starts and
     * Home says why.
     */
    fun onStartOuting() {
        if (starting.value.findingCounty) return
        viewModelScope.launch {
            when {
                outings.inProgress().first() != null -> opened.send(OpenOuting())
                !locator.permitted() && !settings.locationAsked().first() -> starting.update { it.copy(askLocation = true) }
                else -> startNew()
            }
        }
    }

    /**
     * The player has answered the location page: Not now, or Android's prompt, whatever they chose there.
     * The page is not shown again, and the outing starts.
     */
    fun onLocationAnswered() {
        starting.update { it.copy(askLocation = false) }
        viewModelScope.launch {
            settings.setLocationAsked()
            startNew()
        }
    }

    /** The player left the location page with Back: no outing starts, and the page comes back next time. */
    fun onLocationCancelled() {
        starting.update { it.copy(askLocation = false) }
    }

    fun onPackMissingDismissed() {
        starting.update { it.copy(packMissing = false) }
    }

    /**
     * Starts an outing in the county the phone is in, when the app may use its location and the county is
     * found within [LOCATE_TIMEOUT_MILLIS]; else in the home county. Then opens it, or says the home
     * county's pack is missing.
     */
    private suspend fun startNew() {
        starting.update { it.copy(findingCounty = true) }
        try {
            // Null when the app may not use the location: the player said no, which needs no notice.
            val fix = if (locator.permitted()) withTimeoutOrNull(LOCATE_TIMEOUT_MILLIS) { locator.locate() } ?: CountyFix.NoLocation else null
            if (fix is CountyFix.Found) {
                start(fix.countyCode)
                opened.send(OpenOuting())
                return
            }
            val home = settings.settings().first().homeCountyCode
            if (home == null || packs.chartFor(home) == null) {
                starting.update { it.copy(packMissing = true) }
                return
            }
            start(home)
            opened.send(OpenOuting(fallback = fix?.let(::fallbackFor)))
        } finally {
            starting.update { it.copy(findingCounty = false) }
        }
    }

    // No place name yet: the player gives one on the Outing screen if they like.
    private suspend fun start(countyCode: String) {
        outings.start(placeName = "", countyCode = countyCode)
    }

    /** Where starting an outing stands. */
    private data class Starting(val packMissing: Boolean = false, val askLocation: Boolean = false, val findingCounty: Boolean = false)

    internal companion object {
        /** Long enough to keep the figures across a rotation. */
        const val STOP_AFTER_MILLIS = 5_000L

        /** How long a new outing waits for the phone's location before it falls back to the home county. */
        const val LOCATE_TIMEOUT_MILLIS = 5_000L
    }
}

/**
 * The Outing screen is to open, with an outing in progress.
 *
 * @property fallback why a new outing is in the home county; null when it is not, or was in progress already
 */
data class OpenOuting(val fallback: CountyFallback? = null)

/** Why a new outing fell back to the home county, for a [fix] that found no county. */
internal fun fallbackFor(fix: CountyFix): CountyFallback? = when (fix) {
    is CountyFix.Found -> null
    CountyFix.OutsidePacks -> CountyFallback.OUTSIDE_PACKS
    CountyFix.NoLocation -> CountyFallback.NO_LOCATION
}

/** The state a county code names: `US-CO` of `US-CO-059`, as eBird's region codes go. */
internal fun stateCodeOf(countyCode: String): String = countyCode.substringBeforeLast('-')

/** The offer of the server's pack [pack], with how its download stands in [downloads]. */
internal fun packOffer(pack: PackSummary, downloads: Downloads): PackOffer = PackOffer(
    stateCode = pack.stateCode,
    state = pack.name,
    sizeBytes = pack.sizeBytes,
    progress = downloads.running[pack.stateCode],
    failure = downloads.failed?.takeIf { it.stateCode == pack.stateCode }?.outcome,
)

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
