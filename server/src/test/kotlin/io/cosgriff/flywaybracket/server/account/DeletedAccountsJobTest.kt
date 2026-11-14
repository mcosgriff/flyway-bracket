package io.cosgriff.flywaybracket.server.account

import io.cosgriff.flywaybracket.server.league.HandClock
import io.cosgriff.flywaybracket.server.league.InMemoryLeagueStore
import io.cosgriff.flywaybracket.server.league.Leagues
import io.cosgriff.flywaybracket.server.pack.testCharts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** The hourly job over deleted accounts, over stores in memory. All values are made up. */
class DeletedAccountsJobTest {
    private val clock = HandClock(Instant.parse("2027-02-17T19:00:00Z"))
    private val store = InMemoryPlayerStore(clock)
    private val charts = testCharts()
    private val accounts = Accounts(store, Leagues(InMemoryLeagueStore(store), charts, clock), charts, creationLimit = { true }, clock = clock)

    private fun newPlayer(): UUID = UUID.randomUUID().also { store.create(it, "made-up-hash") }

    @Test
    fun `a run deletes again an account listed as deleted, and forgets ids older than 35 days`() {
        val old = newPlayer()
        accounts.delete(SignedInPlayer(old))
        clock.now = clock.now.plus(Duration.ofDays(Accounts.DELETED_IDS_KEEP_DAYS)).plusSeconds(1)
        val restored = newPlayer()
        store.listAsDeleted(restored, clock.now.minus(Duration.ofDays(1)))

        DeletedAccountsJob(accounts).runOnce()

        assertNull(store.profile(restored))
        assertEquals(clock.now.minus(Duration.ofDays(1)), store.deletedAt(restored))
        assertNull(store.deletedAt(old))
    }

    @Test
    fun `an account restored long after its deletion is deleted again before its id is forgotten`() {
        val restored = newPlayer()
        store.listAsDeleted(restored, clock.now.minus(Duration.ofDays(Accounts.DELETED_IDS_KEEP_DAYS + 1)))

        DeletedAccountsJob(accounts).runOnce()

        assertNull(store.profile(restored))
        assertNull(store.deletedAt(restored))
    }

    @Test
    fun `once started, the job runs on its own`() {
        val restored = newPlayer()
        store.listAsDeleted(restored, clock.now)

        DeletedAccountsJob(accounts, period = Duration.ofMillis(10)).use { job ->
            job.start()
            val deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos()
            while (store.profile(restored) != null && System.nanoTime() < deadline) Thread.sleep(10)
        }

        assertNull(store.profile(restored))
    }
}
