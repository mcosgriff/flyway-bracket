package io.cosgriff.flywaybracket.ui.outing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.data.sync.OutingSendState
import io.cosgriff.flywaybracket.data.sync.SightingSync
import io.cosgriff.flywaybracket.domain.outing.OutingLog
import java.time.Clock
import java.time.Duration
import java.time.ZoneId
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * One finished outing (#26): its place, when and how long, the birds seen, and whether they have reached the
 * league server (#43). Its part in a league's bracket comes with leagues (#41).
 */
@HiltViewModel(assistedFactory = SummaryViewModel.Factory::class)
class SummaryViewModel @AssistedInject constructor(
    @Assisted outingId: Long,
    outings: OutingRepository,
    sync: SightingSync,
    clock: Clock,
) : ViewModel() {
    /** Null until the outing is read, and if it is not on the phone. */
    val state: StateFlow<SummaryState?> = combine(outings.log(outingId), sync.outing(outingId)) { log, sent ->
        log?.let { summaryState(it, clock.zone, sent) }
    }
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

/**
 * What the Summary screen shows for [log]; times are in [zone]. An outing still in progress has no length yet,
 * and nothing to say about sending: its sightings are queued when it ends.
 */
internal fun summaryState(log: OutingLog, zone: ZoneId, sent: OutingSendState? = null): SummaryState {
    val outing = log.outing
    val ended = outing.endedAt ?: outing.startedAt
    return SummaryState(
        place = outing.placeName,
        date = outing.date(zone),
        start = outing.startedAt.atZone(zone).toLocalTime(),
        end = ended.atZone(zone).toLocalTime(),
        length = Duration.between(outing.startedAt, ended),
        sightings = log.sightings.map { SummaryRow(it.birdName, it.count) },
        sent = when (sent?.takeIf { outing.endedAt != null }) {
            OutingSendState.SENT -> SummarySent.SENT
            OutingSendState.WAITING -> SummarySent.WAITING
            OutingSendState.ON_PHONE -> SummarySent.ON_PHONE
            OutingSendState.NOTHING_TO_SEND, null -> null
        },
    )
}
