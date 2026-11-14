package io.cosgriff.flywaybracket.ui.home

import androidx.compose.runtime.Immutable
import io.cosgriff.flywaybracket.data.DownloadProgress
import io.cosgriff.flywaybracket.data.PackDownload
import io.cosgriff.flywaybracket.ui.components.MeteredAsk
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.league.CloseReason
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** What Home shows. A part that is `null` or empty has nothing yet, and Home says so. */
@Immutable
data class HomeState(
    /** The player's home county. */
    val region: HomeRegion? = null,
    /** The card at the top, by where the player's league is (#45); null with no league. */
    val hero: LeagueHero? = null,
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
    /** The home county's pack, offered when the server has it and the phone does not (#39). */
    val packOffer: PackOffer? = null,
    /** The offered pack is over 5 MB, the connection is metered, and Home asks first (#39). */
    val askMetered: MeteredAsk? = null,
    /** Sightings have waited more than a day to reach the league server (#43): Home offers to send them now. */
    val sightingsUnsent: Boolean = false,
)

/**
 * The server's pack of the home county's state, which the phone does not have.
 *
 * @property state the state's name
 * @property sizeBytes the size of its file
 * @property progress how far its download has come, or null when it is not downloading
 * @property failure why the last download installed nothing, or null
 */
@Immutable
data class PackOffer(
    val stateCode: String,
    val state: String,
    val sizeBytes: Long,
    val progress: DownloadProgress? = null,
    val failure: PackDownload? = null,
)

/** A county by its name without "County" and its state's postal abbreviation: `Jefferson`, `CO`. */
@Immutable
data class HomeRegion(val county: String, val stateAbbreviation: String)

/**
 * Home's top card for the player's league, one kind for each of its phases (#45): [Forming],
 * [TournamentSummary] while the season runs, [Finished] and [Closed]. With no league Home has none and asks
 * the player to find one.
 */
@Immutable
sealed interface LeagueHero {
    /**
     * The league forms: the players who have joined, of the [needed] a bracket needs; the player's picks made,
     * of the bracket's [matchups]; and when picks lock.
     *
     * @property season null when only the league's summary was fetched, without its dates
     * @property picksLockAt when picks lock, in the phone's zone; null when the league's dates were not fetched
     */
    @Immutable
    data class Forming(
        val season: Season?,
        val players: Int,
        val needed: Int,
        val picksMade: Int,
        val matchups: Int,
        val picksLockAt: LocalDateTime?,
    ) : LeagueHero

    /**
     * The final is decided.
     *
     * @property rank the player's final rank, of [players]; null when the league's table was not fetched
     */
    @Immutable
    data class Finished(val season: Season?, val rank: Int?, val players: Int) : LeagueHero

    /** The league's season never started, and why; [reason] is null only in an older phone's copy. */
    @Immutable
    data class Closed(val leagueName: String, val reason: CloseReason?) : LeagueHero
}

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
) : LeagueHero

/** @property rank the player's place in the table; null before the season, when no one has points, or with no table */
@Immutable
data class LeagueSummary(val name: String, val players: Int, val rank: Int?)

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
