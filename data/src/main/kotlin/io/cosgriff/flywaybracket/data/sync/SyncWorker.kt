package io.cosgriff.flywaybracket.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.time.Duration
import javax.inject.Provider

/**
 * Runs [SightingSync] in the background (#43): sends the queue, and refreshes the league, bracket and results
 * when its input asks ([KEY_REFRESH]). WorkManager runs it once the phone is online, tries again with a
 * backoff after a failure, and keeps it across a restart. Made by [SyncWorkerFactory].
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val sync: SightingSync,
    private val scheduler: SyncScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val run = sync.run(refresh = inputData.getBoolean(KEY_REFRESH, false))
        run.seasonRunning?.let(scheduler::keepRefreshing)
        return if (run.retry) Result.retry() else Result.success()
    }

    companion object {
        /** Whether the run refreshes the league, bracket and results as well as sending the queue. */
        const val KEY_REFRESH = "refresh"
    }
}

/**
 * Makes [SyncWorker] with what it needs from Hilt; any other worker is left to WorkManager's default. Given to
 * WorkManager by the app (`FlywayApp`), in place of `androidx.hilt:hilt-work`.
 */
class SyncWorkerFactory(private val sync: Provider<SightingSync>, private val scheduler: Provider<SyncScheduler>) : WorkerFactory() {
    override fun createWorker(appContext: Context, workerClassName: String, workerParameters: WorkerParameters): ListenableWorker? =
        if (workerClassName == SyncWorker::class.java.name) SyncWorker(appContext, workerParameters, sync.get(), scheduler.get()) else null
}

/** When [SyncWorker] runs. */
interface SyncScheduler {
    /** Sends the queue once the phone is online. A run already waiting, on the network or a backoff, is kept. */
    fun sendSoon()

    /**
     * Refreshes the league, bracket and results and sends the queue, as soon as the phone is online: the app
     * was opened, or the player asked. Takes the place of a run waiting on a backoff.
     */
    fun syncNow()

    /** Refreshes every hour, at most, while [seasonRunning]; stops when it is not. */
    fun keepRefreshing(seasonRunning: Boolean)
}

/** [SyncScheduler] on WorkManager. Every run needs a network; none needs a charger or an idle phone. */
class WorkSyncScheduler(private val context: Context) : SyncScheduler {
    private val work get() = WorkManager.getInstance(context)

    override fun sendSoon() {
        work.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, once(refresh = false))
    }

    override fun syncNow() {
        work.enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, once(refresh = true))
    }

    override fun keepRefreshing(seasonRunning: Boolean) {
        if (seasonRunning) {
            val hourly = PeriodicWorkRequestBuilder<SyncWorker>(REFRESH_EVERY)
                .setConstraints(ONLINE)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
                .setInputData(workDataOf(SyncWorker.KEY_REFRESH to true))
                .build()
            work.enqueueUniquePeriodicWork(HOURLY, ExistingPeriodicWorkPolicy.KEEP, hourly)
        } else {
            work.cancelUniqueWork(HOURLY)
        }
    }

    private fun once(refresh: Boolean) = OneTimeWorkRequestBuilder<SyncWorker>()
        .setConstraints(ONLINE)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF)
        .setInputData(workDataOf(SyncWorker.KEY_REFRESH to refresh))
        .build()

    companion object {
        /** The unique name of the run that sends now, or once online. */
        const val NOW = "sync"

        /** The unique name of the hourly refresh while a season is running. */
        const val HOURLY = "sync-hourly"

        /** A season's results are fetched at most this often. */
        val REFRESH_EVERY: Duration = Duration.ofHours(1)

        /** The first wait after a failure; each later one doubles, up to WorkManager's five hours. */
        val BACKOFF: Duration = Duration.ofSeconds(30)

        private val ONLINE = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}
