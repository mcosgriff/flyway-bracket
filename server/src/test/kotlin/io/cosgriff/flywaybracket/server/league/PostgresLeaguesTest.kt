package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.server.Database
import io.cosgriff.flywaybracket.server.account.Accounts
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.account.PostgresPlayerStore
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.SQLException
import java.util.UUID

/**
 * The league rules against a real Postgres, started in Docker by Testcontainers: here the locks are
 * Postgres's own. Without Docker these tests fail; they are not skipped.
 */
class PostgresLeaguesTest : LeaguesTest() {
    override fun players(): PlayerStore {
        // Each test starts with no players and no leagues: auto-join would find another test's.
        update("TRUNCATE sightings, league_members, leagues, players")
        return PostgresPlayerStore(database.dataSource)
    }

    override fun leagueStore(players: PlayerStore): LeagueStore = PostgresLeagueStore(database.dataSource)

    private fun update(sql: String, vararg parameters: Any): Int = database.dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    private fun count(sql: String, vararg parameters: Any): Int = database.dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { row ->
                row.next()
                row.getInt(1)
            }
        }
    }

    @Test
    fun `a deleted player's league rows are gone`() {
        val owner = newPlayer()
        val league = create(owner)
        val deleted = fill(league, 8).first()
        clock.now = at(START.plusDays(1))
        val leagueId = UUID.fromString(league.id)

        Accounts(PostgresPlayerStore(database.dataSource), leagues).delete(deleted)

        assertEquals(0, count("SELECT count(*) FROM league_members WHERE player_id = ?", deleted.id))
        assertEquals(8, count("SELECT count(*) FROM league_members WHERE league_id = ? AND left_at IS NULL", leagueId))
    }

    @Test
    fun `the table refuses an invite code with a look-alike`() {
        assertThrows(SQLException::class.java) {
            update(
                """
                INSERT INTO leagues (id, name, open, time_zone, invite_code, phase, season_start)
                VALUES (?, 'Thursday Walkers', false, 'America/Denver', 'ABCDEFG0', 'FORMING', DATE '2027-03-01')
                """.trimIndent(),
                UUID.randomUUID(),
            )
        }
    }

    @Test
    fun `the table refuses a start that is not a Monday`() {
        assertThrows(SQLException::class.java) {
            update(
                """
                INSERT INTO leagues (id, name, open, time_zone, invite_code, phase, season_start)
                VALUES (?, 'Thursday Walkers', false, 'America/Denver', 'ABCDEFGH', 'FORMING', DATE '2027-03-02')
                """.trimIndent(),
                UUID.randomUUID(),
            )
        }
    }

    @Test
    fun `leaving after the start keeps the row, marked as left`() {
        val owner = newPlayer()
        val league = create(owner)
        val leaver = fill(league, 8).first()
        clock.now = at(START.plusDays(1))

        leagues.leave(leaver, league.id)

        assertEquals(1, count("SELECT count(*) FROM league_members WHERE player_id = ? AND left_at IS NOT NULL", leaver.id))
    }

    companion object {
        // The Postgres the server is run against (docs/server/running.md), as in DatabaseTest.
        private val postgres = PostgreSQLContainer("postgres:18-alpine")
        private lateinit var database: Database

        @BeforeClass
        @JvmStatic
        fun startPostgres() {
            postgres.start()
            database = Database.connect(postgres.jdbcUrl, postgres.username, postgres.password)
            database.migrate()
        }

        @AfterClass
        @JvmStatic
        fun stopPostgres() {
            if (::database.isInitialized) database.close()
            postgres.stop()
        }
    }
}
