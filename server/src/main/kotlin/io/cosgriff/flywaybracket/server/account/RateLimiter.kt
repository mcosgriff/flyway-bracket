package io.cosgriff.flywaybracket.server.account

import java.time.Clock
import java.time.Duration

/** Says whether one more request from a caller is allowed now. */
fun interface RateLimiter {
    /** Counts the request when it is allowed. */
    fun tryAcquire(key: String): Boolean
}

/**
 * At most [limit] requests per key in each fixed window of time. The windows start at the same moments for
 * every key, counted from the epoch, and the counts of a window are forgotten when it ends. The keys are
 * kept in memory only, never written down.
 */
class FixedWindowRateLimiter(
    private val limit: Int,
    window: Duration,
    private val clock: Clock = Clock.systemUTC(),
) : RateLimiter {
    private val windowMillis = window.toMillis()
    private val counts = HashMap<String, Int>()
    private var currentWindow = Long.MIN_VALUE

    init {
        require(limit > 0) { "limit must be positive" }
        require(windowMillis > 0) { "window must be positive" }
    }

    @Synchronized
    override fun tryAcquire(key: String): Boolean {
        val now = Math.floorDiv(clock.millis(), windowMillis)
        if (now != currentWindow) {
            counts.clear()
            currentWindow = now
        }
        val used = counts[key] ?: 0
        if (used >= limit) return false
        counts[key] = used + 1
        return true
    }
}
