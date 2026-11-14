package io.cosgriff.flywaybracket.server.account

import io.cosgriff.flywaybracket.api.ApiPaths
import io.cosgriff.flywaybracket.server.Database
import io.ktor.client.request.delete
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
 * The account endpoints against a real Postgres, started in Docker by Testcontainers. Without Docker these
 * tests fail; they are not skipped.
 */
class PostgresAccountEndpointsTest : AccountEndpointsTest() {
    override fun store(): PlayerStore = PostgresPlayerStore(database.dataSource)

    /** The row's display name, home county and secret hash, or null when there is no row. */
    private fun row(playerId: String): Triple<String?, String?, String>? = database.dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT display_name, home_county, secret_hash FROM players WHERE id = ?").use { select ->
            select.setObject(1, UUID.fromString(playerId))
            select.executeQuery().use { row ->
                if (row.next()) Triple(row.getString(1), row.getString(2), row.getString(3)) else null
            }
        }
    }

    @Test
    fun `the table holds an Argon2id hash of the secret, not the secret`() = server {
        val account = newAccount()

        val hash = checkNotNull(row(account.playerId)).third

        assertTrue(hash, hash.startsWith("\$argon2id\$"))
        assertFalse(hash.contains(account.secret))
        assertTrue(Argon2idHasher().matches(account.secret, hash))
    }

    @Test
    fun `a profile change is written to the table`() = server {
        val account = newAccount()

        patch(account, """{"displayName":" Player One ","homeCounty":"US-ZZ-001"}""")

        assertEquals("Player One", row(account.playerId)?.first)
        assertEquals("US-ZZ-001", row(account.playerId)?.second)
    }

    @Test
    fun `deleting the account removes its row`() = server {
        val account = newAccount()
        val other = newAccount()

        client.delete(ApiPaths.ACCOUNT) { signedBy(account) }

        assertNull(row(account.playerId))
        assertNotNull(row(other.playerId))
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
