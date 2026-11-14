package io.cosgriff.flywaybracket.data.db

import androidx.room3.Embedded
import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.PrimaryKey
import androidx.room3.Relation

/**
 * The player's league as the server last gave it (#40), so the League tab and Home open with it at once and
 * offline. At most one row: the league `GET /v1/account` names. Its members are in [LeagueMemberEntity].
 *
 * @property phase a `LeaguePhase` name.
 * @property closedBecause a `CloseReason` name; null unless the league is closed.
 * @property timeZone the league's IANA zone id, such as `America/Denver`: every date of the season is in it.
 *   Null, with [seasonStart], when only the league's summary could be fetched.
 * @property seasonStart the season's first Monday, as an ISO date (`2027-03-01`). The season and every other
 *   date follow from it (`SeasonCalendar`).
 * @property fetchedAt when the phone last read the league from the server, in milliseconds since the epoch.
 */
@Entity(tableName = "league")
data class LeagueEntity(
    @PrimaryKey val leagueId: String,
    val name: String,
    val inviteCode: String?,
    val players: Int,
    val capacity: Int,
    val phase: String,
    val closedBecause: String?,
    val timeZone: String?,
    val seasonStart: String?,
    val fetchedAt: Long,
)

/**
 * One row of the league's table, as the server ranked it. [position] keeps the server's order; [you] marks the
 * player. No county and no account id: the server sends neither (ADR 0001).
 */
@Entity(
    tableName = "league_members",
    primaryKeys = ["leagueId", "position"],
    foreignKeys = [
        ForeignKey(entity = LeagueEntity::class, parentColumns = ["leagueId"], childColumns = ["leagueId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class LeagueMemberEntity(
    val leagueId: String,
    val position: Int,
    val memberKey: String,
    val displayName: String,
    val points: Int,
    val rightPicks: Int,
    val rank: Int,
    val you: Boolean,
)

/** The league row with its members. */
data class StoredLeague(
    @Embedded val league: LeagueEntity,
    @Relation(parentColumns = ["leagueId"], entityColumns = ["leagueId"]) val members: List<LeagueMemberEntity>,
)
