package io.cosgriff.flywaybracket.data.db

import android.content.Context
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
            // Made-up county code, place, bird and times.
            helper.createDatabase(version).use { c ->
                if (version == 1) {
                    c.execSQL("INSERT INTO outings (id, countyCode, day) VALUES (1, 'US-ZZ-001', 20000)")
                } else {
                    c.execSQL(
                        "INSERT INTO outings (id, placeName, countyCode, startedAt, endedAt) " +
                            "VALUES (1, 'Test Pond', 'US-ZZ-001', $NOON_OF_DAY_20000, $NOON_OF_DAY_20000)",
                    )
                    c.execSQL(
                        "INSERT INTO sightings (id, outingId, birdName, count, seenAt) " +
                            "VALUES (1, 1, 'Test Warbler', 3, $NOON_OF_DAY_20000)",
                    )
                }
            }
            val db = FlywayDatabase.open(context, name)
            try {
                val current = db.useReaderConnection { it.usePrepared("PRAGMA user_version") { s -> s.step(); s.getInt(0) } }
                assertEquals(exported.last(), current)
                val saved = db.outings().observeFinishedWithSightings().first().single()
                assertEquals(1L, saved.outing.id)
                assertEquals("US-ZZ-001", saved.outing.countyCode)
                // A version 1 outing had only a day: it is finished, at noon UTC of that day, with no place name.
                assertEquals(NOON_OF_DAY_20000, saved.outing.startedAt)
                assertEquals(NOON_OF_DAY_20000, saved.outing.endedAt)
                assertEquals(if (version == 1) "" else "Test Pond", saved.outing.placeName)
                assertEquals(if (version == 1) emptyList() else listOf("Test Warbler" to 3), saved.sightings.map { it.birdName to it.count })
                // The rebuilt table still hands out ids after the rows it kept, and takes sightings.
                val next = db.outings().insert(OutingEntity(placeName = "", countyCode = "US-ZZ-002", startedAt = 1, endedAt = null))
                assertEquals(2L, next)
                db.outings().addOrBump(next, "Sample Sparrow", at = 2)
                assertEquals(1, db.outings().discardInProgress())
                assertEquals(emptyList<SightingEntity>(), db.outings().observeSightings(next).first())
            } finally {
                db.close()
                context.deleteDatabase(name)
            }
        }
    }

    private companion object {
        /** Noon UTC on epoch day 20000, in milliseconds. */
        const val NOON_OF_DAY_20000 = 20000L * 86_400_000L + 43_200_000L
    }
}
