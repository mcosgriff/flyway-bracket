package io.cosgriff.flywaybracket.ui.outing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.domain.outing.Outing
import io.cosgriff.flywaybracket.domain.outing.Sighting
import io.cosgriff.flywaybracket.domain.place.CountyTable
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Where the Outing screen goes next. */
sealed interface OutingExit {
    /** The outing ended; its summary follows. */
    data class Finished(val outingId: Long) : OutingExit

    /** The outing was discarded, or there was none in progress to show. */
    data object Closed : OutingExit
}

/** The outing in progress (#26): its place, its sightings and the time since it started. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OutingViewModel @Inject constructor(
    private val outings: OutingRepository,
    counties: BundledCounties,
    private val clock: Clock,
) : ViewModel() {
    private val current = outings.inProgress().shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), replay = 1)
    private val exits = Channel<OutingExit>(Channel.BUFFERED)

    val state: StateFlow<OutingState> = current.flatMapLatest { outing ->
        if (outing == null) {
            flowOf(OutingState())
        } else {
            outings.sightings(outing.id).map { sightings -> outingState(outing, sightings, counties.table(), clock.zone) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), OutingState())

    /** The time since the outing started, once a second. */
    val elapsed: StateFlow<Duration> = current.flatMapLatest { outing ->
        if (outing == null) flowOf(Duration.ZERO) else everySecond().map { elapsedSince(outing, clock) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), Duration.ZERO)

    /** Emits when the screen should close. */
    val exit: Flow<OutingExit> = exits.receiveAsFlow()

    init {
        // Opened with no outing in progress, as after one ended in another window: nothing to show.
        viewModelScope.launch { if (outings.inProgress().first() == null) exits.send(OutingExit.Closed) }
    }

    fun onRename(place: String) {
        viewModelScope.launch { outings.rename(place) }
    }

    fun onOneMore(sighting: SightingRow) {
        viewModelScope.launch {
            val outing = outings.inProgress().first() ?: return@launch
            outings.addOrBump(outing.id, sighting.bird)
        }
    }

    /** One fewer of a bird seen more than once. A bird seen once is removed with [onRemove]. */
    fun onOneFewer(sighting: SightingRow) {
        if (sighting.count <= Sighting.MIN_COUNT) return
        viewModelScope.launch { outings.setCount(sighting.id, sighting.count - 1) }
    }

    fun onRemove(sighting: SightingRow) {
        viewModelScope.launch { outings.remove(sighting.id) }
    }

    fun onFinish() {
        viewModelScope.launch {
            val outing = outings.inProgress().first() ?: return@launch exits.send(OutingExit.Closed)
            outings.end()
            exits.send(OutingExit.Finished(outing.id))
        }
    }

    fun onDiscard() {
        viewModelScope.launch {
            outings.discard()
            exits.send(OutingExit.Closed)
        }
    }

    private fun everySecond(): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(TICK_MILLIS)
        }
    }

    private companion object {
        /** Long enough to keep the outing across a rotation. */
        const val STOP_AFTER_MILLIS = 5_000L
        const val TICK_MILLIS = 1_000L
    }
}

/** The time from the outing's start to now; never less than zero, should the phone's clock go back. */
internal fun elapsedSince(outing: Outing, clock: Clock): Duration =
    Duration.between(outing.startedAt, clock.instant()).coerceAtLeast(Duration.ZERO)

/**
 * What the Outing screen shows for [outing] and its [sightings], which come in the order first logged:
 * the screen lists the latest first. The county's name comes from the bundled [table].
 */
internal fun outingState(outing: Outing, sightings: List<Sighting>, table: CountyTable, zone: ZoneId): OutingState =
    OutingState(
        place = outing.placeName,
        county = outingCounty(outing.countyCode, table),
        sightings = sightings.asReversed().map { SightingRow(it.id, it.birdName, it.count, it.seenAt.atZone(zone).toLocalTime()) },
    )

internal fun outingCounty(countyCode: String, table: CountyTable): OutingCounty? =
    table.locate(countyCode)?.let { OutingCounty(it.county.name, it.state.abbreviation) }
