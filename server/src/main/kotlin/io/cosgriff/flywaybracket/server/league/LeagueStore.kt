package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.domain.league.LeaguePhase
import io.cosgriff.flywaybracket.domain.league.LeagueStatus
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.sql.DataSource

/**
 * A league as stored.
 *
 * @property status as last settled; [Leagues] settles it for today before using it.
 * @property players how many players are in the league now: those who have not left. Not stored; counted.
 */
data class StoredLeague(
    val id: UUID,
    val name: String,
    val open: Boolean,
    val timeZone: ZoneId,
    val inviteCode: String,
    val status: LeagueStatus,
    val players: Int = 0,
)

/** A player in a league. */
data class StoredMember(val playerId: UUID, val displayName: String)

/** What a locked player has that a league needs. */
data class LockedPlayer(val displayName: String?)

/** The `leagues` and `league_members` tables. Every call blocks; call it off the request threads. */
interface LeagueStore {
    /** Runs [block] in one transaction: commits when it returns, rolls back when it throws. */
    fun <T> transaction(block: LeagueTransaction.() -> T): T
}

/**
 * The calls inside one transaction.
 *
 * **Locks.** A lock is held until the transaction ends. A transaction locks at most one player, and locks
 * them before any league. One that locks several leagues locks them in the order it reads them; only
 * auto-join does that, and auto-joins wait for each other ([lockAutoJoin]).
 */
interface LeagueTransaction {
    /**
     * Locks the player against every other transaction that locks them, so one player's league changes
     * happen one at a time. Null when there is no such player.
     */
    fun lockPlayer(playerId: UUID): LockedPlayer?

    /** Waits for any other auto-join to end, and keeps the next one waiting until this transaction ends. */
    fun lockAutoJoin()

    /** @param lock whether to lock the league, and so its players, against every other transaction that locks it */
    fun league(id: UUID, lock: Boolean = false): StoredLeague?

    fun leagueByInviteCode(code: String): StoredLeague?

    /** The leagues the player is in and has not left, latest start first. */
    fun leaguesOf(playerId: UUID): List<StoredLeague>

    /** Whether the player is in the league and has not left it. */
    fun isMember(leagueId: UUID, playerId: UUID): Boolean

    /** The players in the league who have not left it. */
    fun members(leagueId: UUID): List<StoredMember>

    /** The open leagues stored as forming, earliest start first, then earliest made. */
    fun openFormingLeagues(): List<UUID>

    /** The leagues stored as forming whose start is on or before [date]. */
    fun formingLeaguesStartingBy(date: LocalDate): List<UUID>

    /**
     * Adds a league with no players; [StoredLeague.players] is not read.
     *
     * @return false, adding nothing, when another league has its invite code
     */
    fun insert(league: StoredLeague): Boolean

    fun saveStatus(leagueId: UUID, status: LeagueStatus)

    fun addMember(leagueId: UUID, playerId: UUID)

    /** Takes the player out as if they had never joined. */
    fun removeMember(leagueId: UUID, playerId: UUID)

    /** Keeps the player's row, marked as left: they no longer count. */
    fun markLeft(leagueId: UUID, playerId: UUID)
}

class PostgresLeagueStore(private val dataSource: DataSource) : LeagueStore {
    override fun <T> transaction(block: LeagueTransaction.() -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            PostgresLeagueTransaction(connection).block().also { connection.commit() }
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        } finally {
            connection.autoCommit = true
        }
    }
}

/** At Postgres's default isolation, read committed: each statement sees what was committed before it began. */
private class PostgresLeagueTransaction(private val connection: Connection) : LeagueTransaction {
    override fun lockPlayer(playerId: UUID): LockedPlayer? =
        query("SELECT display_name FROM players WHERE id = ? FOR UPDATE", playerId) { LockedPlayer(it.getString(1)) }.firstOrNull()

    override fun lockAutoJoin() {
        query("SELECT pg_advisory_xact_lock(?)", AUTO_JOIN_LOCK) { }
    }

    override fun league(id: UUID, lock: Boolean): StoredLeague? {
        // The lock is a statement of its own, and the league is read after it. A statement that waits for a
        // lock still answers from the moment it began, so a count read in the locking statement could miss
        // a player another transaction added while this one waited: two players could both take the last
        // seat.
        if (lock && query("SELECT 1 FROM leagues WHERE id = ? FOR UPDATE", id) { }.isEmpty()) return null
        return query("$SELECT_LEAGUE WHERE l.id = ?", id, read = ::readLeague).firstOrNull()
    }

    override fun leagueByInviteCode(code: String): StoredLeague? =
        query("$SELECT_LEAGUE WHERE l.invite_code = ?", code, read = ::readLeague).firstOrNull()

    override fun leaguesOf(playerId: UUID): List<StoredLeague> = query(
        """
        $SELECT_LEAGUE
        JOIN league_members m ON m.league_id = l.id
        WHERE m.player_id = ? AND m.left_at IS NULL
        ORDER BY l.season_start DESC, l.created_at DESC
        """.trimIndent(),
        playerId,
        read = ::readLeague,
    )

    override fun isMember(leagueId: UUID, playerId: UUID): Boolean = query(
        "SELECT 1 FROM league_members WHERE league_id = ? AND player_id = ? AND left_at IS NULL",
        leagueId,
        playerId,
    ) { }.isNotEmpty()

    override fun members(leagueId: UUID): List<StoredMember> = query(
        """
        SELECT m.player_id, COALESCE(p.display_name, '') FROM league_members m
        JOIN players p ON p.id = m.player_id
        WHERE m.league_id = ? AND m.left_at IS NULL
        """.trimIndent(),
        leagueId,
    ) { StoredMember(it.getObject(1, UUID::class.java), it.getString(2)) }

    override fun openFormingLeagues(): List<UUID> = query(
        "SELECT id FROM leagues WHERE open AND phase = 'FORMING' ORDER BY season_start, created_at, id",
    ) { it.getObject(1, UUID::class.java) }

    override fun formingLeaguesStartingBy(date: LocalDate): List<UUID> = query(
        "SELECT id FROM leagues WHERE phase = 'FORMING' AND season_start <= ? ORDER BY season_start, created_at, id",
        date,
    ) { it.getObject(1, UUID::class.java) }

    override fun insert(league: StoredLeague): Boolean = update(
        """
        INSERT INTO leagues (id, name, open, time_zone, invite_code, phase, season_start, postponements)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT (invite_code) DO NOTHING
        """.trimIndent(),
        league.id,
        league.name,
        league.open,
        league.timeZone.id,
        league.inviteCode,
        league.status.phase.name,
        league.status.seasonStart,
        league.status.postponements,
    ) == 1

    override fun saveStatus(leagueId: UUID, status: LeagueStatus) {
        update(
            "UPDATE leagues SET phase = ?, season_start = ?, postponements = ? WHERE id = ?",
            status.phase.name,
            status.seasonStart,
            status.postponements,
            leagueId,
        )
    }

    override fun addMember(leagueId: UUID, playerId: UUID) {
        update("INSERT INTO league_members (league_id, player_id) VALUES (?, ?)", leagueId, playerId)
    }

    override fun removeMember(leagueId: UUID, playerId: UUID) {
        update("DELETE FROM league_members WHERE league_id = ? AND player_id = ?", leagueId, playerId)
    }

    override fun markLeft(leagueId: UUID, playerId: UUID) {
        update(
            "UPDATE league_members SET left_at = now() WHERE league_id = ? AND player_id = ? AND left_at IS NULL",
            leagueId,
            playerId,
        )
    }

    private fun <T> query(sql: String, vararg parameters: Any, read: (ResultSet) -> T): List<T> =
        connection.prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(read(rows)) }
            }
        }

    private fun update(sql: String, vararg parameters: Any): Int = connection.prepareStatement(sql).use { statement ->
        parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeUpdate()
    }

    private fun readLeague(row: ResultSet) = StoredLeague(
        id = row.getObject("id", UUID::class.java),
        name = row.getString("name"),
        open = row.getBoolean("open"),
        timeZone = ZoneId.of(row.getString("time_zone")),
        inviteCode = row.getString("invite_code"),
        status = LeagueStatus(
            phase = LeaguePhase.valueOf(row.getString("phase")),
            seasonStart = row.getObject("season_start", LocalDate::class.java),
            postponements = row.getInt("postponements"),
        ),
        players = row.getInt("players"),
    )

    private companion object {
        /** The key of the advisory lock that auto-joins take; any number no other lock uses. */
        const val AUTO_JOIN_LOCK = 3_200_001L

        const val SELECT_LEAGUE = """
            SELECT l.id, l.name, l.open, l.time_zone, l.invite_code, l.phase, l.season_start, l.postponements,
                (SELECT count(*) FROM league_members c WHERE c.league_id = l.id AND c.left_at IS NULL) AS players
            FROM leagues l"""
    }
}
