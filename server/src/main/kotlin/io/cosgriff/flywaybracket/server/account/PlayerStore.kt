package io.cosgriff.flywaybracket.server.account

import java.util.UUID
import javax.sql.DataSource

/** What a player has told the server about themselves. Either is null until they set it. */
data class StoredProfile(val displayName: String?, val homeCounty: String?)

/** The `players` table. Every call blocks; call it off the request threads. */
interface PlayerStore {
    fun create(id: UUID, secretHash: String)

    /** The stored hash of the player's secret, or null when there is no such player. */
    fun secretHash(id: UUID): String?

    /** Null when there is no such player. */
    fun profile(id: UUID): StoredProfile?

    /**
     * Sets the fields that are not null and leaves the others as they are.
     *
     * @return false when there is no such player
     */
    fun update(id: UUID, displayName: String?, homeCounty: String?): Boolean

    /** @return false when there was no such player */
    fun delete(id: UUID): Boolean
}

class PostgresPlayerStore(private val dataSource: DataSource) : PlayerStore {
    override fun create(id: UUID, secretHash: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO players (id, secret_hash) VALUES (?, ?)").use { insert ->
                insert.setObject(1, id)
                insert.setString(2, secretHash)
                insert.executeUpdate()
            }
        }
    }

    override fun secretHash(id: UUID): String? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT secret_hash FROM players WHERE id = ?").use { select ->
            select.setObject(1, id)
            select.executeQuery().use { row -> if (row.next()) row.getString(1) else null }
        }
    }

    override fun profile(id: UUID): StoredProfile? = dataSource.connection.use { connection ->
        connection.prepareStatement("SELECT display_name, home_county FROM players WHERE id = ?").use { select ->
            select.setObject(1, id)
            select.executeQuery().use { row ->
                if (row.next()) StoredProfile(row.getString(1), row.getString(2)) else null
            }
        }
    }

    override fun update(id: UUID, displayName: String?, homeCounty: String?): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            UPDATE players
            SET display_name = COALESCE(?, display_name), home_county = COALESCE(?, home_county)
            WHERE id = ?
            """.trimIndent(),
        ).use { update ->
            update.setString(1, displayName)
            update.setString(2, homeCounty)
            update.setObject(3, id)
            update.executeUpdate() == 1
        }
    }

    // Deletes the player's row, and with it (ON DELETE CASCADE) their places in leagues, which
    // Accounts.delete has already left (#32), and their sightings (#36). The issue that gives the server
    // picks must extend this (#31): delete the player's picks, and keep their results in a finished season
    // under no name, as "a former player".
    override fun delete(id: UUID): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM players WHERE id = ?").use { delete ->
            delete.setObject(1, id)
            delete.executeUpdate() == 1
        }
    }
}
