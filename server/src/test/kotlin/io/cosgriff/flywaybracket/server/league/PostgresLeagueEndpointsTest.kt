package io.cosgriff.flywaybracket.server.league

import io.cosgriff.flywaybracket.server.Database
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.account.PostgresPlayerStore
import org.junit.AfterClass
import org.junit.BeforeClass
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * The league endpoints against a real Postgres, started in Docker by Testcontainers. Without Docker these
 * tests fail; they are not skipped.
 */
class PostgresLeagueEndpointsTest : LeagueEndpointsTest() {
    override fun players(): PlayerStore {
        // Each test starts with no players and no leagues: auto-join would find another test's.
        database.dataSource.connection.use { connection ->
            connection.createStatement().use { it.executeUpdate("TRUNCATE league_field_teams, league_fields, sightings, league_members, leagues, players") }
        }
        return PostgresPlayerStore(database.dataSource)
    }

    override fun leagueStore(players: PlayerStore): LeagueStore = PostgresLeagueStore(database.dataSource)

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
