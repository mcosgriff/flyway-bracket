package io.cosgriff.flywaybracket.data.db

import android.content.Context
import androidx.room3.AutoMigration
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import java.time.ZoneId
import java.util.UUID

/**
 * What the app saves on the phone. Each version's schema is exported to `data/schemas` and committed; a new
 * version needs a migration (`autoMigrations`, or one written out in [MIGRATIONS] when rows must be
 * converted), which `MigrationTest` runs from every earlier version.
 */
@Database(
    entities = [
        OutingEntity::class,
        SightingEntity::class,
        BracketEntity::class,
        BracketTeamEntity::class,
        BracketPickEntity::class,
        MatchupResultEntity::class,
        SyncQueueEntity::class,
        SyncStateEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // 2 to 3: the league's bracket, the player's picks and the results arrive (#41). New tables only.
    // 3 to 4: Migration3To4.
    autoMigrations = [AutoMigration(from = 2, to = 3)],
)
abstract class FlywayDatabase : RoomDatabase() {
    abstract fun outings(): OutingDao

    abstract fun brackets(): BracketDao

    abstract fun sync(): SyncDao

    /**
     * Outings get a place name and a start and end time in place of their day, and sightings arrive (#25).
     *
     * A version 1 outing had only a day, so it becomes a finished outing with no place name and no length
     * that started at noon UTC on that day: its date reads as the same day in every zone from UTC−12 to
     * UTC+11. The table is rebuilt because the phones the app supports have an SQLite that cannot drop a
     * column.
     */
    object Migration1To2 : Migration(1, 2) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE `outings_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `placeName` TEXT NOT NULL, " +
                    "`countyCode` TEXT NOT NULL, `startedAt` INTEGER NOT NULL, `endedAt` INTEGER)",
            )
            connection.execSQL(
                "INSERT INTO `outings_new` (`id`, `placeName`, `countyCode`, `startedAt`, `endedAt`) " +
                    "SELECT `id`, '', `countyCode`, `day` * $DAY_MILLIS + $NOON_MILLIS, `day` * $DAY_MILLIS + $NOON_MILLIS FROM `outings`",
            )
            connection.execSQL("DROP TABLE `outings`")
            connection.execSQL("ALTER TABLE `outings_new` RENAME TO `outings`")
            connection.execSQL(
                "CREATE TABLE `sightings` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `outingId` INTEGER NOT NULL, " +
                    "`birdName` TEXT NOT NULL, `count` INTEGER NOT NULL, `seenAt` INTEGER NOT NULL, " +
                    "FOREIGN KEY(`outingId`) REFERENCES `outings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            connection.execSQL(
                "CREATE UNIQUE INDEX `index_sightings_outingId_birdName` ON `sightings` (`outingId`, `birdName`)",
            )
        }

        private const val DAY_MILLIS = 86_400_000L
        private const val NOON_MILLIS = DAY_MILLIS / 2
    }

    /**
     * Sightings get a server id, and the queue of what to send to the league server arrives (#43).
     *
     * Each sighting gets a random UUID ([SightingEntity.clientId]); the table is rebuilt, as SQLite cannot add
     * a NOT NULL column without a default. Every sighting of a finished outing is queued for upload, dated the
     * day it was first logged in the phone's zone now: only those inside a league's season are ever sent, and
     * only once the player has an account.
     */
    object Migration3To4 : Migration(3, 4) {
        override suspend fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                "CREATE TABLE `sightings_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `outingId` INTEGER NOT NULL, " +
                    "`birdName` TEXT NOT NULL, `count` INTEGER NOT NULL, `seenAt` INTEGER NOT NULL, `clientId` TEXT NOT NULL, " +
                    "FOREIGN KEY(`outingId`) REFERENCES `outings`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
            )
            val ids = buildList {
                connection.prepare("SELECT `id` FROM `sightings`").use { rows -> while (rows.step()) add(rows.getLong(0)) }
            }
            connection.prepare(
                "INSERT INTO `sightings_new` (`id`, `outingId`, `birdName`, `count`, `seenAt`, `clientId`) " +
                    "SELECT `id`, `outingId`, `birdName`, `count`, `seenAt`, ? FROM `sightings` WHERE `id` = ?",
            ).use { insert ->
                for (id in ids) {
                    insert.bindText(1, UUID.randomUUID().toString())
                    insert.bindLong(2, id)
                    insert.step()
                    insert.reset()
                }
            }
            connection.execSQL("DROP TABLE `sightings`")
            connection.execSQL("ALTER TABLE `sightings_new` RENAME TO `sightings`")
            connection.execSQL("CREATE UNIQUE INDEX `index_sightings_outingId_birdName` ON `sightings` (`outingId`, `birdName`)")
            connection.execSQL("CREATE UNIQUE INDEX `index_sightings_clientId` ON `sightings` (`clientId`)")
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_queue` (`clientId` TEXT NOT NULL, `action` TEXT NOT NULL, " +
                    "`commonName` TEXT NOT NULL, `countyCode` TEXT NOT NULL, `date` TEXT NOT NULL, `queuedAt` INTEGER NOT NULL, " +
                    "`mayBeOnServer` INTEGER NOT NULL, `refused` INTEGER NOT NULL, PRIMARY KEY(`clientId`))",
            )
            connection.execSQL(
                "CREATE TABLE IF NOT EXISTS `sync_state` (`id` INTEGER NOT NULL, `leagueId` TEXT, `leagueSince` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val finished = buildList {
                connection.prepare(
                    "SELECT s.`clientId`, s.`birdName`, o.`countyCode`, s.`seenAt` FROM `sightings` s " +
                        "JOIN `outings` o ON o.`id` = s.`outingId` WHERE o.`endedAt` IS NOT NULL",
                ).use { rows ->
                    while (rows.step()) {
                        add(
                            SightingEntity(
                                outingId = 0,
                                birdName = rows.getText(1),
                                count = 1,
                                seenAt = rows.getLong(3),
                                clientId = rows.getText(0),
                            ).upload(rows.getText(2), zone, now),
                        )
                    }
                }
            }
            connection.prepare(
                "INSERT INTO `sync_queue` (`clientId`, `action`, `commonName`, `countyCode`, `date`, `queuedAt`, `mayBeOnServer`, `refused`) " +
                    "VALUES (?, ?, ?, ?, ?, ?, 0, 0)",
            ).use { insert ->
                for (entry in finished) {
                    insert.bindText(1, entry.clientId)
                    insert.bindText(2, entry.action)
                    insert.bindText(3, entry.commonName)
                    insert.bindText(4, entry.countyCode)
                    insert.bindText(5, entry.date)
                    insert.bindLong(6, entry.queuedAt)
                    insert.step()
                    insert.reset()
                }
            }
        }
    }

    companion object {
        const val FILE_NAME = "flyway.db"

        /** Every migration written by hand, for whoever builds the database. */
        val MIGRATIONS: Array<Migration> = arrayOf(Migration1To2, Migration3To4)

        fun open(context: Context, fileName: String = FILE_NAME): FlywayDatabase =
            Room.databaseBuilder<FlywayDatabase>(context, fileName).addMigrations(*MIGRATIONS).build()
    }
}
