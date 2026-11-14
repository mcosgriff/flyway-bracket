package io.cosgriff.flywaybracket.server.sighting

import java.sql.Connection
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

/**
 * A sighting as stored: everything the server keeps of it (ADR 0001).
 *
 * @property clientId the id the app made for it
 * @property date the day where the player was
 */
data class StoredSighting(val clientId: UUID, val commonName: String, val countyCode: String, val date: LocalDate)

/** The `sightings` table. Every call blocks; call it off the request threads. */
interface SightingStore {
    /**
     * Adds each sighting whose id the player has not used, in one transaction. A sighting whose id the
     * player already has changes nothing, nor does the second of two with one id.
     *
     * @return false, adding nothing, when there is no such player
     */
    fun add(playerId: UUID, sightings: List<StoredSighting>): Boolean

    /** @return false when the player has no sighting with this id */
    fun delete(playerId: UUID, clientId: UUID): Boolean

    /** The player's own sightings. */
    fun of(playerId: UUID): Set<StoredSighting>
}

class PostgresSightingStore(private val dataSource: DataSource) : SightingStore {
    override fun add(playerId: UUID, sightings: List<StoredSighting>): Boolean = transaction { connection ->
        // Keeps the account from being deleted until the sightings are in, so that none outlive it.
        val exists = connection.prepareStatement("SELECT 1 FROM players WHERE id = ? FOR KEY SHARE").use { select ->
            select.setObject(1, playerId)
            select.executeQuery().use { it.next() }
        }
        if (!exists) return@transaction false
        if (sightings.isEmpty()) return@transaction true
        connection.prepareStatement(
            """
            INSERT INTO sightings (player_id, client_id, common_name, county_code, sighted_on)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (player_id, client_id) DO NOTHING
            """.trimIndent(),
        ).use { insert ->
            sightings.forEach { sighting ->
                insert.setObject(1, playerId)
                insert.setObject(2, sighting.clientId)
                insert.setString(3, sighting.commonName)
                insert.setString(4, sighting.countyCode)
                insert.setObject(5, sighting.date)
                insert.addBatch()
            }
            insert.executeBatch()
        }
        true
    }

    override fun delete(playerId: UUID, clientId: UUID): Boolean = dataSource.connection.use { connection ->
        connection.prepareStatement("DELETE FROM sightings WHERE player_id = ? AND client_id = ?").use { delete ->
            delete.setObject(1, playerId)
            delete.setObject(2, clientId)
            delete.executeUpdate() == 1
        }
    }

    override fun of(playerId: UUID): Set<StoredSighting> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            "SELECT client_id, common_name, county_code, sighted_on FROM sightings WHERE player_id = ?",
        ).use { select ->
            select.setObject(1, playerId)
            select.executeQuery().use { rows ->
                buildSet {
                    while (rows.next()) {
                        add(
                            StoredSighting(
                                clientId = rows.getObject(1, UUID::class.java),
                                commonName = rows.getString(2),
                                countyCode = rows.getString(3),
                                date = rows.getObject(4, LocalDate::class.java),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun <T> transaction(block: (Connection) -> T): T = dataSource.connection.use { connection ->
        connection.autoCommit = false
        try {
            block(connection).also { connection.commit() }
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        } finally {
            connection.autoCommit = true
        }
    }
}
