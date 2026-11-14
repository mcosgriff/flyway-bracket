package io.cosgriff.flywaybracket.server

import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.HealthResponse
import io.cosgriff.flywaybracket.server.account.Accounts
import io.cosgriff.flywaybracket.server.account.PostgresPlayerStore
import io.cosgriff.flywaybracket.server.league.Leagues
import io.cosgriff.flywaybracket.server.league.PostgresLeagueStore
import io.cosgriff.flywaybracket.server.pack.noPacks
import io.cosgriff.flywaybracket.server.sighting.PostgresSightingStore
import io.cosgriff.flywaybracket.server.sighting.Sightings
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.testApplication
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.postgresql.PostgreSQLContainer
import java.util.UUID

/**
 * The server against a real Postgres, started in Docker by Testcontainers. Without Docker these tests
 * fail; they are not skipped. All names and values are made up.
 */
class DatabaseTest {
    /** Each column of [table]: its type and whether it may be null. */
    private fun columns(table: String): Map<String, Pair<String, String>> = buildMap {
        database.dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                SELECT column_name, data_type, is_nullable FROM information_schema.columns
                WHERE table_name = ? ORDER BY ordinal_position
                """.trimIndent(),
            ).use { select ->
                select.setString(1, table)
                val rows = select.executeQuery()
                while (rows.next()) put(rows.getString(1), rows.getString(2) to rows.getString(3))
            }
        }
    }

    @Test
    fun `the second migration makes the league tables`() {
        assertEquals(
            mapOf(
                "id" to ("uuid" to "NO"),
                "name" to ("text" to "NO"),
                "open" to ("boolean" to "NO"),
                "time_zone" to ("text" to "NO"),
                "invite_code" to ("text" to "NO"),
                "phase" to ("text" to "NO"),
                "season_start" to ("date" to "NO"),
                "postponements" to ("integer" to "NO"),
                "created_at" to ("timestamp with time zone" to "NO"),
                // Added by the fourth migration.
                "closed_reason" to ("text" to "YES"),
            ),
            columns("leagues"),
        )
        assertEquals(
            mapOf(
                "league_id" to ("uuid" to "NO"),
                "player_id" to ("uuid" to "NO"),
                "joined_at" to ("timestamp with time zone" to "NO"),
                "left_at" to ("timestamp with time zone" to "YES"),
                // Added by the fifth migration.
                "member_key" to ("uuid" to "NO"),
            ),
            columns("league_members"),
        )
    }

    @Test
    fun `the fifth migration makes the picks table`() {
        assertEquals(
            mapOf(
                "league_id" to ("uuid" to "NO"),
                "player_id" to ("uuid" to "NO"),
                "slot" to ("smallint" to "NO"),
                "common_name" to ("text" to "NO"),
            ),
            columns("league_picks"),
        )
    }

    /** Counts only: no column names a player, a county or a sighting. */
    @Test
    fun `the sixth migration makes the results table, and the round homes table`() {
        assertEquals(
            mapOf(
                "league_id" to ("uuid" to "NO"),
                "slot" to ("smallint" to "NO"),
                "upper_bird" to ("text" to "NO"),
                "lower_bird" to ("text" to "NO"),
                "upper_eligible" to ("integer" to "NO"),
                "upper_logged" to ("integer" to "NO"),
                "lower_eligible" to ("integer" to "NO"),
                "lower_logged" to ("integer" to "NO"),
                "winner" to ("text" to "NO"),
                "reason" to ("text" to "NO"),
            ),
            columns("league_results"),
        )
        assertEquals(
            mapOf(
                "league_id" to ("uuid" to "NO"),
                "player_id" to ("uuid" to "NO"),
                "round" to ("text" to "NO"),
                "home_county" to ("text" to "YES"),
            ),
            columns("league_round_homes"),
        )
    }

    @Test
    fun `the fourth migration makes the field tables`() {
        assertEquals(
            mapOf(
                "league_id" to ("uuid" to "NO"),
                "season_start" to ("date" to "NO"),
                "fixed" to ("boolean" to "NO"),
                "candidates" to ("integer" to "NO"),
            ),
            columns("league_fields"),
        )
        assertEquals(
            mapOf(
                "league_id" to ("uuid" to "NO"),
                "quarter" to ("text" to "NO"),
                "seed" to ("integer" to "NO"),
                "common_name" to ("text" to "NO"),
            ),
            columns("league_field_teams"),
        )
    }

    /** ADR 0001: a bird's name, a county and a date, and no column for anything else. */
    @Test
    fun `the third migration makes the sightings table, with nothing beyond the ADR's fields`() {
        assertEquals(
            mapOf(
                "player_id" to ("uuid" to "NO"),
                "client_id" to ("uuid" to "NO"),
                "common_name" to ("text" to "NO"),
                "county_code" to ("text" to "NO"),
                "sighted_on" to ("date" to "NO"),
            ),
            columns("sightings"),
        )
    }

    @Test
    fun `deleting a player deletes their sightings`() {
        val kept = UUID.randomUUID()
        val deleted = UUID.randomUUID()
        database.dataSource.connection.use { connection ->
            listOf(kept, deleted).forEach { id ->
                connection.prepareStatement("INSERT INTO players (id, secret_hash) VALUES (?, ?)").use { insert ->
                    insert.setObject(1, id)
                    insert.setString(2, "made-up-hash")
                    insert.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO sightings (player_id, client_id, common_name, county_code, sighted_on) VALUES (?, ?, 'Test Warbler', 'XX-TS-001', DATE '2027-03-04')",
                ).use { insert ->
                    insert.setObject(1, id)
                    insert.setObject(2, UUID.randomUUID())
                    insert.executeUpdate()
                }
            }
            connection.prepareStatement("DELETE FROM players WHERE id = ?").use { delete ->
                delete.setObject(1, deleted)
                delete.executeUpdate()
            }
            connection.prepareStatement("SELECT player_id FROM sightings WHERE player_id IN (?, ?)").use { select ->
                select.setObject(1, kept)
                select.setObject(2, deleted)
                val rows = select.executeQuery()
                assertTrue(rows.next())
                assertEquals(kept, rows.getObject(1, UUID::class.java))
                assertFalse(rows.next())
            }
        }
    }

    @Test
    fun `the first migration makes the players table`() {
        val columns = columns("players")

        assertEquals(
            mapOf(
                "id" to ("uuid" to "NO"),
                "display_name" to ("text" to "YES"),
                "home_county" to ("text" to "YES"),
                "secret_hash" to ("text" to "NO"),
                "created_at" to ("timestamp with time zone" to "NO"),
            ),
            columns,
        )
    }

    @Test
    fun `a new player needs only an id and a secret hash`() {
        val id = UUID.randomUUID()
        database.dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO players (id, secret_hash) VALUES (?, ?)").use { insert ->
                insert.setObject(1, id)
                insert.setString(2, "made-up-hash")
                assertEquals(1, insert.executeUpdate())
            }
            connection.prepareStatement("SELECT display_name, home_county, created_at FROM players WHERE id = ?").use { select ->
                select.setObject(1, id)
                val row = select.executeQuery()
                assertTrue(row.next())
                assertNull(row.getString("display_name"))
                assertNull(row.getString("home_county"))
                assertNotNull(row.getTimestamp("created_at"))
            }
        }
    }

    @Test
    fun `migrating again applies nothing`() {
        assertEquals(0, database.migrate())
    }

    @Test
    fun `health says the database answers`() = testApplication {
        val packs = noPacks()
        val leagues = Leagues(PostgresLeagueStore(database.dataSource), packs)
        val sightings = Sightings(PostgresSightingStore(database.dataSource), packs)
        application { module(database, Accounts(PostgresPlayerStore(database.dataSource), leagues, packs), leagues, packs, sightings, version = "1.2.3") }

        val health = ApiJson.decodeFromString<HealthResponse>(client.get(ApiPaths.HEALTH).bodyAsText())

        assertEquals(HealthResponse(version = "1.2.3", database = true), health)
    }

    @Test
    fun `a closed pool does not answer`() {
        val closed = Database.connect(postgres.jdbcUrl, postgres.username, postgres.password)
        assertTrue(closed.answers())

        closed.close()

        assertFalse(closed.answers())
    }

    companion object {
        // The Postgres the server is run against (docs/server/running.md). Keep the two the same.
        private val postgres = PostgreSQLContainer("postgres:18-alpine")
        private lateinit var database: Database

        @BeforeClass
        @JvmStatic
        fun startPostgres() {
            postgres.start()
            database = Database.connect(postgres.jdbcUrl, postgres.username, postgres.password)
            assertEquals(6, database.migrate())
        }

        @AfterClass
        @JvmStatic
        fun stopPostgres() {
            if (::database.isInitialized) database.close()
            postgres.stop()
        }
    }
}
