package io.cosgriff.flywaybracket.server.account

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * When the server starts and then once an hour (#16): deletes again every account listed as deleted that a
 * restore from a backup brought back ([Accounts.deleteAgainListed]), then forgets the ids of accounts
 * deleted more than [Accounts.DELETED_IDS_KEEP_DAYS] days ago ([Accounts.forgetOldDeletions]).
 *
 * The hosted server sleeps when no one uses it and is woken every hour, so in practice this runs at each
 * wake. A failure is logged and tried again on the next run. The log gives counts only, never an id.
 */
class DeletedAccountsJob(
    private val accounts: Accounts,
    private val period: Duration = PERIOD,
    private val log: Logger = LoggerFactory.getLogger(DeletedAccountsJob::class.java),
) : AutoCloseable {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "deleted-accounts").apply { isDaemon = true }
    }

    /** Runs the job now and then every [period] until [close]. */
    fun start() {
        executor.scheduleWithFixedDelay(::runOnce, 0, period.toMillis(), TimeUnit.MILLISECONDS)
    }

    /** One run: deletes again first, so that an id is never forgotten while its account still exists. */
    fun runOnce() {
        try {
            val again = accounts.deleteAgainListed()
            if (again > 0) log.info("Deleted again {} accounts listed as deleted", again)
        } catch (failure: Exception) {
            log.error("Could not delete again the accounts listed as deleted", failure)
            return
        }
        try {
            val forgotten = accounts.forgetOldDeletions()
            if (forgotten > 0) log.info("Forgot {} deleted accounts' ids", forgotten)
        } catch (failure: Exception) {
            log.error("Could not forget old deleted accounts' ids", failure)
        }
    }

    override fun close() {
        executor.shutdownNow()
    }

    companion object {
        val PERIOD: Duration = Duration.ofHours(1)
    }
}
