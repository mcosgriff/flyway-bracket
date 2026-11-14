package io.cosgriff.flywaybracket.data.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import io.cosgriff.flywaybracket.api.LeagueState
import io.cosgriff.flywaybracket.data.sync.SyncFixture.Companion.IN_SEASON
import io.cosgriff.flywaybracket.data.sync.SyncFixture.Companion.ROUND_1
import javax.inject.Provider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [SyncWorker] as WorkManager runs it, made by [SyncWorkerFactory], against a server in memory (#43). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncWorkerTest {
    private class RecordingScheduler : SyncScheduler {
        val refreshing = mutableListOf<Boolean>()

        override fun sendSoon() = Unit

        override fun syncNow() = Unit

        override fun keepRefreshing(seasonRunning: Boolean) {
            refreshing += seasonRunning
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fixture = SyncFixture("sync_worker_test")
    private val scheduler = RecordingScheduler()

    @After
    fun close() {
        fixture.close()
    }

    private fun run(refresh: Boolean = true): ListenableWorker.Result = runBlocking {
        TestListenableWorkerBuilder<SyncWorker>(context)
            .setWorkerFactory(SyncWorkerFactory(Provider { fixture.sync }, Provider { scheduler }))
            .setInputData(workDataOf(SyncWorker.KEY_REFRESH to refresh))
            .build()
            .doWork()
    }

    @Test
    fun `a run sends the finished outing's sightings and succeeds`() = runBlocking {
        fixture.outing(IN_SEASON, "Test Warbler", "Sample Sparrow")

        assertEquals(ListenableWorker.Result.success(), run())

        assertEquals(setOf("Test Warbler", "Sample Sparrow"), fixture.stored.values.map { it.commonName }.toSet())
        assertEquals(emptyList<Any>(), fixture.db.sync().uploads())
        // In season: results are fetched every hour.
        assertEquals(listOf(true), scheduler.refreshing)
    }

    @Test
    fun `a run that cannot reach the server asks to be retried, and the next one sends`() = runBlocking {
        fixture.outing(IN_SEASON, "Test Warbler")
        fixture.online = false

        assertEquals(ListenableWorker.Result.retry(), run())
        assertEquals(1, fixture.db.sync().uploads().size)

        fixture.online = true
        assertEquals(ListenableWorker.Result.success(), run())
        assertEquals(listOf("Test Warbler"), fixture.stored.values.map { it.commonName })
        assertEquals(emptyList<Any>(), fixture.db.sync().uploads())
    }

    @Test
    fun `a batch sent again after a lost answer is not counted twice`() = runBlocking {
        fixture.outing(ROUND_1, "Test Warbler", "Sample Sparrow")
        fixture.outing(IN_SEASON, "Example Thrush")
        // The first run fetches the league, so the second has nothing but the batch to send.
        // Two: the client tries a lost answer once more by itself (#93), and that answer is lost too.
        fixture.loseAnswers = 2

        assertEquals(ListenableWorker.Result.retry(), run())
        assertEquals(3, fixture.db.sync().uploads().size)

        assertEquals(ListenableWorker.Result.success(), run(refresh = false))
        val sent = fixture.uploads()
        assertEquals(3, sent.size)
        // The same ids each time: the server keeps one sighting per id.
        assertEquals(1, sent.map { upload -> upload.sightings.map { it.clientId }.toSet() }.distinct().size)
        assertEquals(3, fixture.stored.size)
        assertEquals(emptyList<Any>(), fixture.db.sync().uploads())

        // And nothing is sent again.
        assertEquals(ListenableWorker.Result.success(), run(refresh = false))
        assertEquals(3, fixture.uploads().size)
    }

    @Test
    fun `a sighting removed after it was sent is deleted on the server`() = runBlocking {
        val id = fixture.outing(IN_SEASON, "Test Warbler", "Sample Sparrow")
        run()
        val warbler = fixture.db.outings().sightingsOf(id).single { it.birdName == "Test Warbler" }

        fixture.outings.remove(warbler.id)
        assertEquals(ListenableWorker.Result.success(), run(refresh = false))

        assertEquals(listOf(warbler.clientId), fixture.deletes())
        assertEquals(listOf("Sample Sparrow"), fixture.stored.values.map { it.commonName })
        assertEquals(emptyList<Any>(), fixture.db.sync().deletes())
    }

    @Test
    fun `a delete the server has no sighting for is done`() = runBlocking {
        val id = fixture.outing(IN_SEASON, "Test Warbler")
        val warbler = fixture.db.outings().sightingsOf(id).single()
        // Tried, so it may be on the server; it is not.
        fixture.db.sync().claim(listOf(warbler.clientId))

        fixture.outings.remove(warbler.id)
        assertEquals(ListenableWorker.Result.success(), run(refresh = false))

        assertEquals(listOf(warbler.clientId), fixture.deletes())
        assertEquals(emptyList<Any>(), fixture.db.sync().deletes())
    }

    @Test
    fun `out of season, the hourly refresh stops`() {
        fixture.league = LeagueState.FINISHED
        assertEquals(ListenableWorker.Result.success(), run())
        assertEquals(listOf(false), scheduler.refreshing)
    }

    @Test
    fun `the scheduler asks WorkManager for runs that need a network`() {
        val config = Configuration.Builder().setExecutor(SynchronousExecutor()).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        val work = WorkManager.getInstance(context)
        val scheduler = WorkSyncScheduler(context)

        scheduler.syncNow()
        scheduler.sendSoon()
        val now = work.getWorkInfosForUniqueWork(WorkSyncScheduler.NOW).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, now.state)
        assertEquals(NetworkType.CONNECTED, now.constraints.requiredNetworkType)

        scheduler.keepRefreshing(true)
        val hourly = work.getWorkInfosForUniqueWork(WorkSyncScheduler.HOURLY).get().single()
        assertEquals(WorkInfo.State.ENQUEUED, hourly.state)
        assertTrue(hourly.periodicityInfo!!.repeatIntervalMillis >= WorkSyncScheduler.REFRESH_EVERY.toMillis())

        scheduler.keepRefreshing(false)
        assertEquals(WorkInfo.State.CANCELLED, work.getWorkInfosForUniqueWork(WorkSyncScheduler.HOURLY).get().single().state)
    }
}
