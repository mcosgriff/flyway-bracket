package io.cosgriff.flywaybracket.ui.outing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.domain.outing.OutingLog
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * One finished outing (#26): its place, when and how long, and the birds seen. Its part in a league's
 * bracket comes with leagues (#41).
 */
@HiltViewModel(assistedFactory = SummaryViewModel.Factory::class)
class SummaryViewModel @AssistedInject constructor(
    @Assisted outingId: Long,
    outings: OutingRepository,
    clock: Clock,
) : ViewModel() {
    /** Null until the outing is read, and if it is not on the phone. */
    val state: StateFlow<SummaryState?> = outings.log(outingId)
        .map { log -> log?.let { summaryState(it, clock.zone) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_AFTER_MILLIS), null)

    @AssistedFactory
    interface Factory {
        fun create(outingId: Long): SummaryViewModel
    }

    private companion object {
        /** Long enough to keep the summary across a rotation. */
        const val STOP_AFTER_MILLIS = 5_000L
    }
}

/** What the Summary screen shows for [log]; times are in [zone]. An outing still in progress has no length yet. */
internal fun summaryState(log: OutingLog, zone: ZoneId): SummaryState {
    val outing = log.outing
    val ended = outing.endedAt ?: outing.startedAt
    return SummaryState(
        place = outing.placeName,
        date = outing.date(zone),
        start = outing.startedAt.atZone(zone).toLocalTime(),
        end = ended.atZone(zone).toLocalTime(),
        length = Duration.between(outing.startedAt, ended),
        sightings = log.sightings.map { SummaryRow(it.birdName, it.count) },
    )
}
