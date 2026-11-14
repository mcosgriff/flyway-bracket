package io.cosgriff.flywaybracket.ui.league

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.cosgriff.flywaybracket.data.LeagueRepository
import io.cosgriff.flywaybracket.data.server.ServerActivity
import io.cosgriff.flywaybracket.data.server.ServerResult
import io.cosgriff.flywaybracket.domain.league.LeagueName
import io.cosgriff.flywaybracket.domain.league.LeagueNameCheck
import io.cosgriff.flywaybracket.domain.league.LeagueTimeZone
import io.cosgriff.flywaybracket.domain.league.SeasonStart
import io.cosgriff.flywaybracket.domain.league.SeasonStartCheck
import io.cosgriff.flywaybracket.ui.components.message
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A league just created: its name and the code to share. */
@Immutable
data class CreatedLeague(val name: String, val inviteCode: String?)

/**
 * What Create a league shows (#40).
 *
 * @property timeZone the league's zone id: the phone's at first
 * @property seasonStart the first Monday: the second Monday from today at first
 * @property problem why the last try failed; null when it did not
 * @property created the league, once it is created: the screen then shows its invite code
 */
@Immutable
data class CreateLeagueState(
    val name: String = "",
    val open: Boolean = true,
    val timeZone: String,
    val seasonStart: LocalDate,
    val creating: Boolean = false,
    @StringRes val problem: Int? = null,
    val created: CreatedLeague? = null,
) {
    val nameCheck: LeagueNameCheck get() = LeagueName.check(name)

    val canCreate: Boolean get() = nameCheck == LeagueNameCheck.VALID && !creating
}

/**
 * Create a league (#40): a name, open or invite-only, a start date and a time zone, checked with the rules
 * the server uses (`domain/league`), then sent. Only reached from the League tab, so the phone has an account.
 */
@HiltViewModel
class LeagueCreateViewModel @Inject constructor(
    private val leagues: LeagueRepository,
    private val clock: Clock,
    activity: ServerActivity,
) : ViewModel() {
    /** Whether the server is most likely waking while the league is being created (#93). */
    val waking: StateFlow<Boolean> = activity.waking

    private val mutableState = MutableStateFlow(
        // The phone's zone, unless it is one the server would refuse, such as a bare offset.
        (LeagueTimeZone.of(clock.zone.id)?.id ?: FALLBACK_ZONE).let { zone ->
            CreateLeagueState(timeZone = zone, seasonStart = SeasonStart.forNewOpenLeague(today(zone)))
        },
    )
    val state: StateFlow<CreateLeagueState> = mutableState.asStateFlow()

    /** Today in the league's zone: the start date is judged from it. */
    fun today(): LocalDate = today(state.value.timeZone)

    fun onName(name: String) {
        mutableState.update { it.copy(name = name, problem = null) }
    }

    fun onOpen(open: Boolean) {
        mutableState.update { it.copy(open = open) }
    }

    /** A start the rule refuses is not taken: the date picker offers only those it allows. */
    fun onSeasonStart(start: LocalDate) {
        if (SeasonStart.check(start, today()) != SeasonStartCheck.VALID) return
        mutableState.update { it.copy(seasonStart = start, problem = null) }
    }

    /** A new zone moves today; a start too soon for it moves to the second Monday from that today. */
    fun onTimeZone(id: String) {
        val zone = LeagueTimeZone.of(id)?.id ?: return
        val today = today(zone)
        mutableState.update { current ->
            val start = current.seasonStart.takeIf { SeasonStart.check(it, today) == SeasonStartCheck.VALID } ?: SeasonStart.forNewOpenLeague(today)
            current.copy(timeZone = zone, seasonStart = start, problem = null)
        }
    }

    fun onCreate() {
        val current = state.value
        if (!current.canCreate || current.created != null) return
        mutableState.update { it.copy(creating = true, problem = null) }
        viewModelScope.launch {
            val created = leagues.create(current.name, current.open, ZoneId.of(current.timeZone), current.seasonStart)
            mutableState.update {
                when (created) {
                    is ServerResult.Ok -> it.copy(creating = false, created = CreatedLeague(created.value.name, created.value.inviteCode))
                    is ServerResult.Failed -> it.copy(creating = false, problem = created.problem.message)
                }
            }
        }
    }

    private fun today(zone: String): LocalDate = LocalDate.now(clock.withZone(ZoneId.of(zone)))

    private companion object {
        const val FALLBACK_ZONE = "UTC"
    }
}
