package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.server.Database
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.account.PostgresPlayerStore
import io.cosgriff.flywaybracket.server.sighting.PostgresSightingStore
import io.cosgriff.flywaybracket.server.sighting.SightingStore
import org.junit.AfterClass
import org.junit.Assert.assertThrows
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import java.sql.SQLException
import java.util.UUID

/**
 * Deciding rounds against a real Postgres, started in Docker by Testcontainers: the league store reads the
 * sightings the sighting store wrote, from the table. Without Docker these tests fail; they are not skipped.
 */
class PostgresRoundResultsTest : RoundResultsTest() {
    override fun players(): PlayerStore {
        update(
            "TRUNCATE league_round_homes, league_results, league_picks, league_field_teams, league_fields, sightings, " +
                "league_members, leagues, players",
        )
        return PostgresPlayerStore(database.dataSource)
    }

    override fun leagueStore(players: PlayerStore, sightings: SightingStore): LeagueStore = PostgresLeagueStore(database.dataSource)

    override fun sightingStore(players: PlayerStore): SightingStore = PostgresSightingStore(database.dataSource)

    private fun update(sql: String, vararg parameters: Any): Int = database.dataSource.connection.use { connection ->
        connection.prepareStatement(sql).use { statement ->
            parameters.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    @Test
    fun `the results table refuses a winner that is not in the matchup`() {
        val league = UUID.randomUUID()
        update(
            """
            INSERT INTO leagues (id, name, open, time_zone, invite_code, phase, season_start)
            VALUES (?, 'Thursday Walkers', false, 'America/Denver', 'ABCDEFGH', 'RUNNING', DATE '2027-03-01')
            """.trimIndent(),
            league,
        )

        assertThrows(SQLException::class.java) {
            update(
                """
                INSERT INTO league_results
                    (league_id, slot, upper_bird, lower_bird, upper_eligible, upper_logged, lower_eligible, lower_logged, winner, reason)
                VALUES (?, 0, 'Test Warbler 20', 'Test Warbler 05', 8, 1, 8, 0, 'Test Teal 20', 'HIGHER_SHARE')
                """.trimIndent(),
                league,
            )
        }
        assertThrows(SQLException::class.java) {
            update(
                """
                INSERT INTO league_results
                    (league_id, slot, upper_bird, lower_bird, upper_eligible, upper_logged, lower_eligible, lower_logged, winner, reason)
                VALUES (?, 0, 'Test Warbler 20', 'Test Warbler 05', 2, 3, 8, 0, 'Test Warbler 20', 'HIGHER_SHARE')
                """.trimIndent(),
                league,
            )
        }
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
