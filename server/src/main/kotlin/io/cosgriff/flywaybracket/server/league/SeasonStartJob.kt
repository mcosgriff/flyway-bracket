package io.cosgriff.flywaybracket.server.league

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Once a minute, settles every league whose field is due to be fixed or whose start has arrived in its own
 * time zone (#34), so that seeding and the start happen on time even when no one reads the league.
 *
 * It settles through [Leagues.settle], the same step a read takes, under the league's lock: a league
 * settled by a read first, or by this job twice, is seeded once. A league that fails is logged and tried
 * again on the next run; the others go on.
 */
class SeasonStartJob(
    private val leagues: Leagues,
    private val period: Duration = PERIOD,
    private val log: Logger = LoggerFactory.getLogger(SeasonStartJob::class.java),
) : AutoCloseable {
    private val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "season-start").apply { isDaemon = true }
    }

    /** Runs the job now and then every [period] until [close]. */
    fun start() {
        executor.scheduleWithFixedDelay(::runOnce, 0, period.toMillis(), TimeUnit.MILLISECONDS)
    }

    /** One run: settles each league that is due, each in a transaction of its own. */
    fun runOnce() {
        val due: List<UUID> = try {
            leagues.dueToSettle()
        } catch (failure: Exception) {
            log.error("Could not list the leagues due to start", failure)
            return
        }
        for (id in due) {
            try {
                leagues.settle(id)
            } catch (failure: Exception) {
                log.error("Could not settle league {}", id, failure)
            }
        }
    }

    override fun close() {
        executor.shutdownNow()
    }

    companion object {
        val PERIOD: Duration = Duration.ofMinutes(1)
    }
}
