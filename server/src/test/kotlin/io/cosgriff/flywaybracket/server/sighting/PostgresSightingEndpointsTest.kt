package io.cosgriff.flywaybracket.server.sighting

import io.cosgriff.flywaybracket.server.Database
import io.cosgriff.flywaybracket.server.account.PlayerStore
import io.cosgriff.flywaybracket.server.account.PostgresPlayerStore
import org.junit.AfterClass
import org.junit.BeforeClass
import org.testcontainers.postgresql.PostgreSQLContainer

/**
 * The sighting endpoints against a real Postgres, started in Docker by Testcontainers: here a deleted
 * account's sightings go by the table's cascade. Without Docker these tests fail; they are not skipped.
 */
class PostgresSightingEndpointsTest : SightingEndpointsTest() {
    override fun players(): PlayerStore = PostgresPlayerStore(database.dataSource)

    override fun sightingStore(players: PlayerStore): SightingStore = PostgresSightingStore(database.dataSource)

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
