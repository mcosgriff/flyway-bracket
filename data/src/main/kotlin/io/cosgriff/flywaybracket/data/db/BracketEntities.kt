package io.cosgriff.flywaybracket.data.db

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import androidx.room3.Relation

/**
 * A league's bracket as the server last gave it (#41): one row per league, with its birds, the player's picks
 * and the decided matchups in the tables below. Deleting it deletes them.
 *
 * @property preview whether the field could still change when it was fetched (`BracketResponse.preview`).
 * @property seasonStart the season's first Monday, as an ISO date (`2027-03-01`), when picks lock.
 * @property timeZone the league's IANA zone id, such as `America/Denver`: every date of the season is in it.
 * @property picksLocked whether the server last said picks are locked.
 * @property picksUnsent whether the phone holds picks the server has not taken yet.
 * @property picksNotice why the phone's picks last changed without the player, as a `PicksNotice` name; null
 *   when there is nothing to tell.
 */
@Entity(tableName = "brackets")
data class BracketEntity(
    @PrimaryKey val leagueId: String,
    val preview: Boolean,
    val seasonStart: String,
    val timeZone: String,
    val picksLocked: Boolean,
    val picksUnsent: Boolean,
    val picksNotice: String?,
)

/** One of a bracket's 64 birds (`Team` in `:domain`). [quarter] is a `BirdGroup` name. */
@Entity(
    tableName = "bracket_teams",
    primaryKeys = ["leagueId", "commonName"],
    foreignKeys = [
        ForeignKey(entity = BracketEntity::class, parentColumns = ["leagueId"], childColumns = ["leagueId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class BracketTeamEntity(val leagueId: String, val commonName: String, val quarter: String, val seed: Int)

/** The bird the player picked to win the matchup at [slot] (`MatchupSlot.index`, 0 to 62). */
@Entity(
    tableName = "bracket_picks",
    primaryKeys = ["leagueId", "slot"],
    foreignKeys = [
        ForeignKey(entity = BracketEntity::class, parentColumns = ["leagueId"], childColumns = ["leagueId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class BracketPickEntity(val leagueId: String, val slot: Int, val bird: String)

/**
 * How the matchup at [slot] was decided, as the server's round results give it: the winner, both birds'
 * tallies, the reason (a `Decision.Reason` name) and what the player's pick earned.
 */
@Entity(
    tableName = "bracket_results",
    primaryKeys = ["leagueId", "slot"],
    foreignKeys = [
        ForeignKey(entity = BracketEntity::class, parentColumns = ["leagueId"], childColumns = ["leagueId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class MatchupResultEntity(
    val leagueId: String,
    val slot: Int,
    val winner: String,
    val upperEligible: Int,
    val upperLogged: Int,
    val lowerEligible: Int,
    val lowerLogged: Int,
    val reason: String,
    val roundPoints: Int,
    val upsetBonus: Int,
)

/** A bracket row with everything that hangs from it. */
data class StoredBracket(
    @Embedded val bracket: BracketEntity,
    @Relation(parentColumns = ["leagueId"], entityColumns = ["leagueId"]) val teams: List<BracketTeamEntity>,
    @Relation(parentColumns = ["leagueId"], entityColumns = ["leagueId"]) val picks: List<BracketPickEntity>,
    @Relation(parentColumns = ["leagueId"], entityColumns = ["leagueId"]) val results: List<MatchupResultEntity>,
)
