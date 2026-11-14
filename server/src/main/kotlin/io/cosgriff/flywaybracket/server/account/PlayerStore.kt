package io.cosgriff.flywaybracket.server.account

import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
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

    /**
     * Deletes the player and, in the same step, lists their id as deleted, with the time, unless it is
     * listed already: a list kept so that a restore from a backup can be followed by deleting them again
     * (docs/server/backups.md). Nothing else about the player is kept.
     *
     * @return false when there was no such player
     */
    fun delete(id: UUID): Boolean

    /**
     * The ids listed as deleted that still name a player. None, unless the database was restored from a
     * backup taken before they were deleted and the list was then copied in.
     */
    fun deletedButPresent(): List<UUID>

    /**
     * Forgets the ids listed as deleted before [cutoff].
     *
     * @return how many were forgotten
     */
    fun forgetDeletedBefore(cutoff: Instant): Int
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
    // One statement, so the row cannot go without its id being listed. An id listed already, as after a
    // restore, keeps the time it was first deleted.
    override fun delete(id: UUID): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            WITH gone AS (DELETE FROM players WHERE id = ? RETURNING id),
            listed AS (INSERT INTO deleted_players (player_id) SELECT id FROM gone ON CONFLICT (player_id) DO NOTHING)
            SELECT count(*) FROM gone
            """.trimIndent(),
        ).use { delete ->
            delete.setObject(1, id)
            delete.executeQuery().use { row ->
                row.next()
                row.getInt(1) == 1
            }
        }
    }

    override fun deletedButPresent(): List<UUID> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT d.player_id FROM deleted_players d JOIN players p ON p.id = d.player_id ORDER BY d.player_id",
        ).use { select ->
            select.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.getObject(1, UUID::class.java)) }
            }
        }
    }

    override fun forgetDeletedBefore(cutoff: Instant): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM deleted_players WHERE deleted_at < ?").use { delete ->
            delete.setObject(1, OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
            delete.executeUpdate()
        }
    }
}
