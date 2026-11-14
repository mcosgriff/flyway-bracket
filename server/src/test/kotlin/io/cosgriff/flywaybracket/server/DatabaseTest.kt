package io.cosgriff.flywaybracket.server

import io.cosgriff.flywaybracket.api.ApiJson
import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.api.HealthResponse
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
    @Test
    fun `the first migration makes the players table`() {
        val columns = buildMap {
            database.dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    val rows = statement.executeQuery(
                        """
                        SELECT column_name, data_type, is_nullable FROM information_schema.columns
                        WHERE table_name = 'players' ORDER BY ordinal_position
                        """.trimIndent(),
                    )
                    while (rows.next()) put(rows.getString(1), rows.getString(2) to rows.getString(3))
                }
            }
        }

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
        application { module(database, version = "1.2.3") }

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
            assertEquals(1, database.migrate())
        }

        @AfterClass
        @JvmStatic
        fun stopPostgres() {
            if (::database.isInitialized) database.close()
            postgres.stop()
        }
    }
}
