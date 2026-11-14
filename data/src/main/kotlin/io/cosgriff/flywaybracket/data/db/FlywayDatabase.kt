package io.cosgriff.flywaybracket.data.db

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase

/**
 * What the app saves on the phone. Each version's schema is exported to `data/schemas` and committed; a new
 * version needs a migration (`autoMigrations`), which `MigrationTest` runs from every earlier version.
 */
@Database(
    entities = [OutingEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class FlywayDatabase : RoomDatabase() {
    abstract fun outings(): OutingDao

    companion object {
        const val FILE_NAME = "flyway.db"

        fun open(context: Context): FlywayDatabase =
            Room.databaseBuilder<FlywayDatabase>(context, FILE_NAME).build()
    }
}
