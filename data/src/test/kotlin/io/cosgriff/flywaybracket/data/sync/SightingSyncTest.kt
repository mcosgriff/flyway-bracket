package io.cosgriff.flywaybracket.data.sync

import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.data.sync.SyncFixture.Companion.BEFORE_SEASON
import io.cosgriff.flywaybracket.data.sync.SyncFixture.Companion.IN_SEASON
import io.cosgriff.flywaybracket.data.sync.SyncFixture.Companion.ROUND_1
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What [SightingSync] sends, when, and what it tells the screens (#43). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SightingSyncTest {
    private var fixture = SyncFixture("sighting_sync_test")

    @After
    fun close() {
        fixture.close()
    }

    @Test
    fun `with no account nothing is sent and nothing is called`() = runBlocking {
        fixture.close()
        fixture = SyncFixture("sighting_sync_test_no_account", withAccount = false)
        val outing = fixture.outing(IN_SEASON, "Test Warbler")

        assertEquals(SyncRun(retry = false, seasonRunning = false), fixture.sync.run(refresh = true))

        assertEquals(emptyList<Any>(), fixture.requests)
        assertEquals(SyncQueueState(due = 0, waitingSince = null), fixture.sync.queueState().first())
        assertEquals(OutingSendState.ON_PHONE, fixture.sync.outing(outing).first())
    }

    @Test
    fun `an upload carries the id, bird, county and date and nothing else`() = runBlocking {
        val outing = fixture.outing(IN_SEASON, "Test Warbler")
        val clientId = fixture.db.outings().sightingsOf(outing).single().clientId

        fixture.sync.run(refresh = true)

        assertEquals(
            """{"sightings":[{"clientId":"$clientId","commonName":"Test Warbler","countyCode":"US-ZZ-001","date":"2027-03-09"}]}""",
            fixture.uploadBodies().single(),
        )
    }

    @Test
    fun `only sightings inside the league's season are sent, and one from before joining goes once there is a league`() = runBlocking {
        fixture.league = null
        val early = fixture.outing(BEFORE_SEASON, "Test Warbler")
        val joined = fixture.outing(ROUND_1, "Sample Sparrow")

        fixture.sync.run(refresh = true)
        assertEquals(emptyList<Any>(), fixture.uploads())
        assertEquals(OutingSendState.ON_PHONE, fixture.sync.outing(joined).first())

        // The player joins.
        fixture.league = LeagueState.IN_SEASON
        fixture.sync.run(refresh = true)

        assertEquals(listOf("Sample Sparrow"), fixture.stored.values.map { it.commonName })
        assertEquals(OutingSendState.SENT, fixture.sync.outing(joined).first())
        assertEquals(OutingSendState.ON_PHONE, fixture.sync.outing(early).first())
        assertEquals(SyncQueueState(due = 0, waitingSince = null), fixture.sync.queueState().first())
    }

    @Test
    fun `a sighting the server refuses is set aside and the rest are sent`() = runBlocking {
        val outing = fixture.outing(IN_SEASON, "Test Warbler", "Unknown Test Bird", "Sample Sparrow")
        fixture.unknownBirds += "Unknown Test Bird"

        assertEquals(SyncRun(retry = false, seasonRunning = true), fixture.sync.run(refresh = true))

        assertEquals(setOf("Test Warbler", "Sample Sparrow"), fixture.stored.values.map { it.commonName }.toSet())
        assertEquals(emptyList<Any>(), fixture.db.sync().uploads())
        assertEquals(OutingSendState.ON_PHONE, fixture.sync.outing(outing).first())

        // Not tried again.
        val sent = fixture.uploads().size
        fixture.sync.run(refresh = false)
        assertEquals(sent, fixture.uploads().size)
    }

    @Test
    fun `the queue says what waits and since when`() = runBlocking {
        // The league is learned before the outing.
        fixture.sync.run(refresh = true)
        fixture.online = false
        val started = Instant.parse("2027-03-11T08:00:00Z")
        val outing = fixture.outing(started, "Test Warbler")
        val empty = fixture.outing(started)
        assertEquals(OutingSendState.NOTHING_TO_SEND, fixture.sync.outing(empty).first())

        assertEquals(SyncRun(retry = true, seasonRunning = null), fixture.sync.run(refresh = true))

        // Queued when the outing ended, 30 minutes after it started.
        assertEquals(SyncQueueState(due = 1, waitingSince = started + Duration.ofMinutes(30)), fixture.sync.queueState().first())
        assertEquals(OutingSendState.WAITING, fixture.sync.outing(outing).first())
    }

    @Test
    fun `a sighting is late once it has waited more than a day`() {
        val since = Instant.parse("2027-03-10T08:00:00Z")
        val waiting = SyncQueueState(due = 1, waitingSince = since)
        assertFalse(waiting.isLate(since + Duration.ofHours(24)))
        assertTrue(waiting.isLate(since + Duration.ofHours(24) + Duration.ofSeconds(1)))
        assertFalse(SyncQueueState(due = 0, waitingSince = null).isLate(since + Duration.ofDays(9)))
    }

    @Test
    fun `a sighting from before the league was set waits from then`() = runBlocking {
        fixture.league = null
        fixture.outing(ROUND_1, "Test Warbler")
        fixture.sync.run(refresh = true)
        fixture.online = false
        fixture.league = LeagueState.IN_SEASON
        assertNull(fixture.sync.queueState().first().waitingSince)

        // The league is learned, then the network goes.
        fixture.online = true
        val learned = Instant.parse("2027-03-11T09:00:00Z")
        fixture.clock.now = learned
        fixture.loseAnswers = 1
        fixture.sync.run(refresh = true)

        assertEquals(SyncQueueState(due = 1, waitingSince = learned), fixture.sync.queueState().first())
    }
}
