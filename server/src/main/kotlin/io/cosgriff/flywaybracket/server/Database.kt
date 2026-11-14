package io.cosgriff.flywaybracket.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import java.sql.SQLException
import javax.sql.DataSource

/** Whether the database answers, asked by the health check. */
fun interface DatabaseCheck {
    /** Blocks for a few seconds at most. */
    fun answers(): Boolean
}

/**
 * The server's Postgres: a pool of connections, with the schema brought up to date by the SQL files in
 * `resources/db/migration`.
 */
class Database private constructor(private val pool: HikariDataSource) : DatabaseCheck, AutoCloseable {
    val dataSource: DataSource get() = pool

    /**
     * Applies every migration not yet applied.
     *
     * @return how many were applied by this call
     */
    fun migrate(): Int = Flyway.configure().dataSource(pool).load().migrate().migrationsExecuted

    override fun answers(): Boolean = try {
        pool.connection.use { it.isValid(VALID_WITHIN_SECONDS) }
    } catch (_: SQLException) {
        false
    }

    override fun close() = pool.close()

    companion object {
        /** How long a request waits for a free connection before it fails. */
        private const val CONNECTION_TIMEOUT_MS = 5_000L
        private const val VALID_WITHIN_SECONDS = 2

        /**
         * Opens the pool. Fails at once when the database cannot be reached: a server with no database
         * should not start.
         */
        fun connect(config: ServerConfig): Database = connect(config.databaseUrl, config.databaseUser, config.databasePassword)

        fun connect(url: String, user: String, password: String): Database {
            val settings = HikariConfig().apply {
                jdbcUrl = url
                username = user
                this.password = password
                connectionTimeout = CONNECTION_TIMEOUT_MS
            }
            return Database(HikariDataSource(settings))
        }
    }
}
