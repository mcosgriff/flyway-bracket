package io.cosgriff.flywaybracket.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * What the app saves on the phone. Each version's schema is exported to `data/schemas` and committed; a new
 * version needs a migration (`autoMigrations`, or one written out in [MIGRATIONS] when rows must be
 * converted), which `MigrationTest` runs from every earlier version.
 */
@Database(
    entities = [OutingEntity::class, SightingEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class FlywayDatabase : RoomDatabase() {
    abstract fun outings(): OutingDao

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

    companion object {
        const val FILE_NAME = "flyway.db"

        /** Every migration written by hand, for whoever builds the database. */
        val MIGRATIONS: Array<Migration> = arrayOf(Migration1To2)

        fun open(context: Context, fileName: String = FILE_NAME): FlywayDatabase =
            Room.databaseBuilder<FlywayDatabase>(context, fileName).addMigrations(*MIGRATIONS).build()
    }
}
