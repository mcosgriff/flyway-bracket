package io.cosgriff.flywaybracket.ui.outing

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.domain.outing.Sighting
import io.cosgriff.flywaybracket.domain.outing.searchBirds
import io.cosgriff.flywaybracket.domain.pack.CountyChart
import io.cosgriff.flywaybracket.domain.pack.TimeBin
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Add sighting sheet (#26): the birds of the outing's county, from its pack, for this week, narrowed
 * by what the player types. Tapping one logs it on the outing in progress.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AddSightingViewModel @Inject constructor(
    private val outings: OutingRepository,
    packs: PackRepository,
    counties: BundledCounties,
    clock: Clock,
) : ViewModel() {
    /** What the player has typed. Snapshot state, so the text field never lags behind the keyboard. */
    var query by mutableStateOf("")
        private set

    private val outing = outings.inProgress().shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), replay = 1)

    val state: StateFlow<AddSightingState> = outing.flatMapLatest { outing ->
        if (outing == null) return@flatMapLatest flowOf(AddSightingState())
        val chart = packs.chartFor(outing.countyCode)
        val county = outingCounty(outing.countyCode, counties.table())
        combine(outings.sightings(outing.id), snapshotFlow { query }) { sightings, query ->
            addSightingState(chart, TimeBin.of(LocalDate.now(clock)), query, sightings, county)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), AddSightingState())

    fun onQuery(text: String) {
        query = text
    }

    /** Logs one of [bird] on the outing in progress, and clears the search for the next one. */
    fun onPick(bird: String) {
        query = ""
        viewModelScope.launch {
            val outing = outing.first() ?: return@launch
            outings.addOrBump(outing.id, bird)
        }
    }

    /** The sheet closed without a bird: the next one starts with an empty search. */
    fun onDismiss() {
        query = ""
    }

    private companion object {
        /** Long enough to keep the list across a rotation. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/**
 * The sheet's list for the county's [chart] in the bin [bin], narrowed by [query]. Each bird shows how
 * many of it are among [sightings] already. With no [chart], the county's pack is not on the phone.
 */
internal fun addSightingState(
    chart: CountyChart?,
    bin: TimeBin,
    query: String,
    sightings: List<Sighting>,
    county: OutingCounty?,
): AddSightingState {
    if (chart == null) return AddSightingState(county = county, hasPack = false)
    val logged = sightings.associate { it.birdName to it.count }
    return AddSightingState(
        county = county,
        birds = searchBirds(chart, bin, query).map { BirdRow(it.commonName, it.expected, logged[it.commonName] ?: 0) },
    )
}
