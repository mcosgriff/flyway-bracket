package io.cosgriff.flywaybracket.ui.bracket

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import io.cosgriff.flywaybracket.domain.bracket.BracketView
import io.cosgriff.flywaybracket.domain.bracket.DrawPosition
import io.cosgriff.flywaybracket.domain.bracket.Round
import io.cosgriff.flywaybracket.domain.bracket.Season
import io.cosgriff.flywaybracket.domain.bracket.Team
import java.time.LocalDate
import java.time.LocalDateTime

/** The three ways the Bracket tab draws the bracket (#42): by round, one quarter's tree, and the whole field. */
enum class BracketLayout { LIST, TREE, FIELD }

/** Why the picks changed without the player picking, which the tab says once (#41). */
enum class BracketNotice {
    /** Picks locked before the phone could send the latest; the picks the server held are the player's. */
    LOCKED_BEFORE_SENT,

    /** The preview was seeded again, and the picks of matchups whose birds changed were cleared. */
    BRACKET_CHANGED,
}

/** What the Bracket tab shows. */
@Immutable
sealed interface BracketScreenState {
    /** The player's league and bracket are being read. */
    data object Loading : BracketScreenState

    /**
     * No bracket to show: the player is in no league, or in one that closed ([forming] false), or in one still
     * forming whose field cannot be seeded yet ([forming] true). The tab points to League.
     */
    data class NoLeague(val forming: Boolean) : BracketScreenState

    /** The league or its bracket could not be read, and none is on the phone. */
    data class Failed(@StringRes val message: Int) : BracketScreenState

    data class Ready(val bracket: BracketState) : BracketScreenState
}

/**
 * The player's bracket in their league (#42).
 *
 * @property locked whether picks are locked: from then on they are read-only, and a matchup opens its page
 * @property locksAt when picks lock, in the phone's zone
 * @property previewUntil the day the field is fixed, while it is a preview that can still change; else null
 * @property liveRound the round being played; null before the season and after the final
 * @property roundEnds each round's Sunday, in the league's zone
 * @property picksUnsent the phone holds picks the server has not taken yet
 * @property problem why the last update or pick failed; null when it did not
 * @property confirm a pick that would clear later picks, waiting for the player to say yes
 */
@Immutable
data class BracketState(
    val view: BracketView,
    val season: Season,
    val locked: Boolean,
    val locksAt: LocalDateTime,
    val previewUntil: LocalDate?,
    val liveRound: Round?,
    val roundEnds: Map<Round, LocalDate>,
    val picksUnsent: Boolean = false,
    val notice: BracketNotice? = null,
    @StringRes val problem: Int? = null,
    val confirm: PickChange? = null,
) {
    /** The round the tab opens on: the one being played, else the final once it is decided, else the first. */
    val firstRound: Round
        get() = liveRound ?: if (view.isDecided(Round.FINAL)) Round.FINAL else Round.ROUND_OF_64
}

/**
 * A pick that clears later picks ([io.cosgriff.flywaybracket.domain.bracket.BracketPicks.clearedBy]).
 *
 * @property cleared the picks it clears: each matchup's round and the bird picked there, by round
 */
@Immutable
data class PickChange(val at: DrawPosition, val bird: Team, val cleared: List<ClearedPick>)

@Immutable
data class ClearedPick(val round: Round, val bird: String)
