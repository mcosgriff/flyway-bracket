package io.cosgriff.flywaybracket.data.db

import android.content.Context
import androidx.room3.Room
import androidx.room3.testing.MigrationTestHelper
import androidx.room3.useReaderConnection
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every schema the app has shipped upgrades to the current one with its rows intact.
 *
 * For each exported version in `data/schemas`, a database is made at that version with one outing, then
 * opened as the current database: Room runs each migration in between and checks the result against the
 * current schema. What the player logged lives only on the phone until the app has its own sync, so a
 * migration that loses a row loses it for good. A new schema version is covered as soon as its file is in the
 * folder; a version whose tables change needs its seed below to follow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    // Gradle runs a module's tests from the module's folder.
    private val exported: List<Int> =
        File("schemas/${FlywayDatabase::class.java.name}").listFiles { f -> f.extension == "json" }.orEmpty()
            .map { it.nameWithoutExtension.toInt() }.sorted()

    @Test
    fun `the current schema is exported`() {
        assertTrue("no exported schemas found", exported.isNotEmpty())
        assertEquals(exported.indices.map { it + 1 }, exported)
    }

    @Test
    fun `every exported version opens as the current database with its rows`() = runBlocking {
        for (version in exported) {
            val name = "migration-$version.db"
            context.deleteDatabase(name)
            val helper = MigrationTestHelper(
                InstrumentationRegistry.getInstrumentation(),
                context.getDatabasePath(name),
                AndroidSQLiteDriver(),
                FlywayDatabase::class,
            )
            // A made-up county code and day.
            helper.createDatabase(version).use { c ->
                c.execSQL("INSERT INTO outings (id, countyCode, day) VALUES (1, 'US-ZZ-001', 20000)")
            }
            val db = Room.databaseBuilder<FlywayDatabase>(context, name).build()
            try {
                val current = db.useReaderConnection { it.usePrepared("PRAGMA user_version") { s -> s.step(); s.getInt(0) } }
                assertEquals(exported.last(), current)
                val outing = db.outings().observeAll().first().single()
                assertEquals("US-ZZ-001", outing.countyCode)
                assertEquals(20000L, outing.day)
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
    }
}
