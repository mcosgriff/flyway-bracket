package io.cosgriff.flywaybracket.ui.home

import androidx.compose.runtime.Immutable
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import java.time.Duration
import java.time.LocalDate

/** What Home shows. A part that is `null` or empty has nothing yet, and Home says so. */
@Immutable
data class HomeState(
    /** The player's home county. */
    val region: HomeRegion? = null,
    val tournament: TournamentSummary? = null,
    val league: LeagueSummary? = null,
    val season: SeasonStats = SeasonStats(),
    val recentOutings: List<OutingRow> = emptyList(),
    /** Whether an outing is in progress, which the start button then continues. */
    val outingInProgress: Boolean = false,
    /** The player tried to start an outing and the home county's pack is not on the phone (#26). */
    val packMissing: Boolean = false,
    /** Start outing was tapped, and the page that explains the use of the phone's location comes first (#27). */
    val askLocation: Boolean = false,
    /** A new outing is waiting for the phone's location to find its county (#27). */
    val findingCounty: Boolean = false,
    /** Sightings have waited more than a day to reach the league server (#43): Home offers to send them now. */
    val sightingsUnsent: Boolean = false,
)

/** A county by its name without "County" and its state's postal abbreviation: `Jefferson`, `CO`. */
@Immutable
data class HomeRegion(val county: String, val stateAbbreviation: String)

/** Where the player's bracket stands in the tournament being played. */
@Immutable
data class TournamentSummary(
    val season: Season,
    val round: Round,
    val points: Int,
    val picksRight: Int,
    val picksDecided: Int,
    /** Birds the player picked this round and hasn't logged yet. */
    val picksNotYetSeen: Int,
)

@Immutable
data class LeagueSummary(val name: String, val players: Int, val rank: Int)

@Immutable
data class SeasonStats(val species: Int = 0, val lifers: Int = 0, val outings: Int = 0)

@Immutable
data class OutingRow(
    val id: Long,
    val place: String,
    val date: LocalDate,
    val length: Duration,
    val species: Int,
)
