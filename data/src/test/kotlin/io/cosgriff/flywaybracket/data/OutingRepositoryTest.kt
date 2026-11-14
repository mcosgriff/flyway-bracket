package io.cosgriff.flywaybracket.data

import android.content.Context
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.data.db.FlywayDatabase
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Places, county codes, bird names and times are made up.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutingRepositoryTest {
    /** A clock the test moves by hand. */
    private class TestClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now

        fun pass(minutes: Long) {
            now += Duration.ofMinutes(minutes)
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder<FlywayDatabase>(context).build()
    private val start: Instant = Instant.parse("2027-04-18T14:00:00Z")
    private val clock = TestClock(start)
    private val outings = OutingRepository(db.outings(), clock)

    @After
    fun close() {
        db.close()
    }

    /** A finished outing of half an hour with one of each bird, then a gap of an hour. */
    private suspend fun finishedOuting(place: String, vararg birds: String): Long {
        val id = outings.start(place, "US-ZZ-001").id
        birds.forEach { outings.addOrBump(id, it) }
        clock.pass(minutes = 30)
        outings.end()
        clock.pass(minutes = 60)
        return id
    }

    @Test
    fun `nothing saved reads as no outing in progress and none finished`() = runBlocking {
        assertNull(outings.inProgress().first())
        assertEquals(emptyList<Any>(), outings.finished().first())
        assertEquals(emptyList<Any>(), outings.finishedLogs().first())
    }

    @Test
    fun `a started outing is in progress from now with its place and county trimmed`() = runBlocking {
        val started = outings.start("  Test Pond ", " US-ZZ-001")
        assertEquals("Test Pond", started.placeName)
        assertEquals("US-ZZ-001", started.countyCode)
        assertEquals(start, started.startedAt)
        assertNull(started.endedAt)
        assertEquals(started, outings.inProgress().first())
        assertEquals(emptyList<Any>(), outings.finished().first())
    }

    @Test
    fun `an outing may have no place name but needs a county`() = runBlocking {
        assertEquals("", outings.start("", "US-ZZ-001").placeName)
        outings.discard()
        assertThrows(IllegalArgumentException::class.java) { runBlocking { outings.start("Test Pond", " ") } }
        assertNull(outings.inProgress().first())
    }

    @Test
    fun `starting while an outing is in progress returns that outing and starts no second one`() = runBlocking {
        val first = outings.start("Test Pond", "US-ZZ-001")
        clock.pass(minutes = 5)
        val second = outings.start("Sample Marsh", "US-ZZ-002")
        assertEquals(first, second)
        outings.end()
        assertNull(outings.inProgress().first())
        assertEquals(listOf("Test Pond"), outings.finished().first().map { it.placeName })
    }

    @Test
    fun `a bird logged for the first time is a sighting of one`() = runBlocking {
        val id = outings.start("Test Pond", "US-ZZ-001").id
        clock.pass(minutes = 3)
        val sighting = outings.addOrBump(id, " Test Warbler ")
        assertEquals("Test Warbler", sighting.birdName)
        assertEquals(1, sighting.count)
        assertEquals(start.plusSeconds(180), sighting.seenAt)
        assertEquals(listOf(sighting), outings.sightings(id).first())
    }

    @Test
    fun `a bird logged again is the same sighting with a higher count and its first time`() = runBlocking {
        val id = outings.start("Test Pond", "US-ZZ-001").id
        val first = outings.addOrBump(id, "Test Warbler")
        outings.addOrBump(id, "Sample Sparrow")
        clock.pass(minutes = 10)
        val again = outings.addOrBump(id, "Test Warbler")
        assertEquals(first.copy(count = 2), again)
        assertEquals(listOf("Test Warbler" to 2, "Sample Sparrow" to 1), outings.sightings(id).first().map { it.birdName to it.count })
    }

    @Test
    fun `a sighting needs a bird and an outing that exists`() = runBlocking {
        val id = outings.start("Test Pond", "US-ZZ-001").id
        assertThrows(IllegalArgumentException::class.java) { runBlocking { outings.addOrBump(id, "  ") } }
        assertThrows(Exception::class.java) { runBlocking { outings.addOrBump(id + 1, "Test Warbler") } }
        assertEquals(emptyList<Any>(), outings.sightings(id).first())
    }

    @Test
    fun `a sighting's count can be changed but not below one`() = runBlocking {
        val id = outings.start("Test Pond", "US-ZZ-001").id
        val sighting = outings.addOrBump(id, "Test Warbler")
        outings.setCount(sighting.id, 7)
        assertEquals(listOf(7), outings.sightings(id).first().map { it.count })
        assertThrows(IllegalArgumentException::class.java) { runBlocking { outings.setCount(sighting.id, 0) } }
        assertEquals(listOf(7), outings.sightings(id).first().map { it.count })
    }

    @Test
    fun `a removed sighting is gone and the bird can be logged afresh`() = runBlocking {
        val id = outings.start("Test Pond", "US-ZZ-001").id
        val warbler = outings.addOrBump(id, "Test Warbler")
        outings.addOrBump(id, "Test Warbler")
        outings.addOrBump(id, "Sample Sparrow")
        outings.remove(warbler.id)
        assertEquals(listOf("Sample Sparrow"), outings.sightings(id).first().map { it.birdName })
        assertEquals(1, outings.addOrBump(id, "Test Warbler").count)
    }

    @Test
    fun `ending the outing in progress makes it a finished outing that ended now`() = runBlocking {
        val id = outings.start("Test Pond", "US-ZZ-001").id
        clock.pass(minutes = 45)
        outings.end()
        assertNull(outings.inProgress().first())
        val finished = outings.finished().first().single()
        assertEquals(id, finished.id)
        assertEquals(start.plusSeconds(45 * 60), finished.endedAt)
    }

    @Test
    fun `ending with no outing in progress changes nothing`() = runBlocking {
        finishedOuting("Test Pond")
        val before = outings.finished().first()
        clock.pass(minutes = 20)
        outings.end()
        assertEquals(before, outings.finished().first())
    }

    @Test
    fun `discarding the outing in progress deletes it and its sightings`() = runBlocking {
        val id = outings.start("Test Pond", "US-ZZ-001").id
        outings.addOrBump(id, "Test Warbler")
        outings.discard()
        assertNull(outings.inProgress().first())
        assertEquals(emptyList<Any>(), outings.finished().first())
        assertEquals(emptyList<Any>(), outings.sightings(id).first())
    }

    @Test
    fun `discarding never deletes a finished outing`() = runBlocking {
        val id = finishedOuting("Test Pond", "Test Warbler")
        outings.discard()
        assertEquals(listOf(id), outings.finished().first().map { it.id })
        assertEquals(listOf("Test Warbler"), outings.sightings(id).first().map { it.birdName })
    }

    @Test
    fun `finished outings come newest first without the one in progress`() = runBlocking {
        finishedOuting("Test Pond")
        finishedOuting("Sample Marsh")
        outings.start("Made-up Ridge", "US-ZZ-001")
        assertEquals(listOf("Sample Marsh", "Test Pond"), outings.finished().first().map { it.placeName })
    }

    @Test
    fun `finished logs carry each outing's sightings in the order first logged`() = runBlocking {
        val pond = finishedOuting("Test Pond", "Test Warbler", "Sample Sparrow")
        val marsh = finishedOuting("Sample Marsh", "Made-up Heron")
        val ridge = outings.start("Made-up Ridge", "US-ZZ-001").id
        outings.addOrBump(ridge, "Example Tanager")
        val logs = outings.finishedLogs().first()
        assertEquals(listOf(marsh, pond), logs.map { it.outing.id })
        assertEquals(listOf(listOf("Made-up Heron"), listOf("Test Warbler", "Sample Sparrow")), logs.map { log -> log.sightings.map { it.birdName } })
    }

    @Test
    fun `the outing in progress can be renamed, trimmed, and a finished one cannot`() = runBlocking {
        finishedOuting("Test Pond")
        outings.start("Sample Marsh", "US-ZZ-001")
        outings.rename("  Made-up Ridge ")
        assertEquals("Made-up Ridge", outings.inProgress().first()?.placeName)
        outings.rename("")
        assertEquals("", outings.inProgress().first()?.placeName)
        assertEquals(listOf("Test Pond"), outings.finished().first().map { it.placeName })
    }

    @Test
    fun `one outing's log has its sightings in the order first logged, and a missing outing is null`() = runBlocking {
        val pond = finishedOuting("Test Pond", "Test Warbler", "Sample Sparrow")
        val log = outings.log(pond).first()
        assertEquals(pond, log?.outing?.id)
        assertEquals(listOf("Test Warbler", "Sample Sparrow"), log?.sightings?.map { it.birdName })
        assertNull(outings.log(pond + 1).first())
    }
}
