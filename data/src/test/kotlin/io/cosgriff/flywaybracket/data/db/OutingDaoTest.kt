package io.cosgriff.flywaybracket.data.db

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// County codes and days are made up.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutingDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder<FlywayDatabase>(context).build()

    @After
    fun close() {
        db.close()
    }

    @Test
    fun `outings come back newest first`() = runBlocking {
        db.outings().insert(OutingEntity(countyCode = "US-ZZ-001", day = 20000))
        db.outings().insert(OutingEntity(countyCode = "US-ZZ-002", day = 20003))
        assertEquals(listOf("US-ZZ-002", "US-ZZ-001"), db.outings().observeAll().first().map { it.countyCode })
    }
}
