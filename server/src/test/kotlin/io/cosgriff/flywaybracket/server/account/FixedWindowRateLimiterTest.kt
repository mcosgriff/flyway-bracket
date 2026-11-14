package io.cosgriff.flywaybracket.server.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/** The addresses are from the ranges set aside for documentation. */
class FixedWindowRateLimiterTest {
    /** A clock the test moves by hand. */
    private class HandClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = this

        override fun instant(): Instant = now
    }

    private val clock = HandClock(Instant.parse("2027-03-01T10:00:00Z"))
    private val limiter = FixedWindowRateLimiter(limit = 3, window = Duration.ofHours(1), clock = clock)

    @Test
    fun `allows the limit in a window, then refuses`() {
        val answers = List(5) { limiter.tryAcquire(ADDRESS) }

        assertEquals(listOf(true, true, true, false, false), answers)
    }

    @Test
    fun `each address has its own count`() {
        repeat(3) { limiter.tryAcquire(ADDRESS) }

        assertFalse(limiter.tryAcquire(ADDRESS))
        assertTrue(limiter.tryAcquire(OTHER_ADDRESS))
    }

    @Test
    fun `the count starts again in the next window`() {
        repeat(3) { limiter.tryAcquire(ADDRESS) }

        clock.now = Instant.parse("2027-03-01T10:59:59Z")
        assertFalse(limiter.tryAcquire(ADDRESS))

        clock.now = Instant.parse("2027-03-01T11:00:00Z")
        assertTrue(limiter.tryAcquire(ADDRESS))
    }

    private companion object {
        const val ADDRESS = "192.0.2.10"
        const val OTHER_ADDRESS = "198.51.100.20"
    }
}
