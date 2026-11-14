package io.cosgriff.flywaybracket.ui.home

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.data.AccountRepository
import io.cosgriff.flywaybracket.data.BracketRepository
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.CountyFix
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.data.account.AccountStore
import io.cosgriff.flywaybracket.data.account.StoredAccount
import io.cosgriff.flywaybracket.data.db.BracketEntity
import io.cosgriff.flywaybracket.data.db.SyncStateEntity
import io.cosgriff.flywaybracket.data.server.ServerClient
import io.cosgriff.flywaybracket.data.server.serverHttpClient
import io.cosgriff.flywaybracket.data.sync.SightingSync
import io.cosgriff.flywaybracket.data.sync.SyncScheduler
import io.cosgriff.flywaybracket.data.db.FlywayDatabase
import io.cosgriff.flywaybracket.domain.outing.Outing
import io.cosgriff.flywaybracket.domain.outing.OutingLog
import io.cosgriff.flywaybracket.domain.outing.Sighting
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.testStatePack
import io.cosgriff.flywaybracket.domain.place.CountyTableParser
import io.cosgriff.flywaybracket.ui.FakeCountyLocator
import io.cosgriff.flywaybracket.ui.outing.CountyFallback
import io.ktor.client.engine.mock.MockEngine
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Places, county codes, bird names and times are made up.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HomeViewModelTest {
    private val zone = ZoneId.of("America/Denver")
    private val today = LocalDate.of(2027, 4, 18)
    private var nextId = 1L

    /** A finished outing that started at noon on [date] in [zone]. */
    private fun log(date: LocalDate, vararg birds: String, place: String = "Test Pond", minutes: Long = 30): OutingLog {
        val id = nextId++
        val start = date.atTime(12, 0).atZone(zone).toInstant()
        return OutingLog(
            Outing(id, place, "US-ZZ-001", start, start.plus(Duration.ofMinutes(minutes))),
            birds.map { Sighting(nextId++, id, it, count = 1, seenAt = start) },
        )
    }

    // The view model's timers run on this clock, which a test moves on by hand.
    private val scheduler = TestCoroutineScheduler()

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))
    }

    @After
    fun resetMain() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no outings give Home its empty state`() {
        assertEquals(HomeState(), homeState(emptyList(), today, zone))
    }

    @Test
    fun `this season is the calendar year today is in`() {
        val logs = listOf(
            log(LocalDate.of(2027, 3, 14), "Made-up Heron", "Sample Sparrow"),
            log(LocalDate.of(2027, 1, 1), "Sample Sparrow", "Example Tanager"),
            log(LocalDate.of(2026, 12, 31), "Sample Sparrow", "Test Warbler"),
        )
        // Sample Sparrow was logged in 2026, so it is a species of the season but not a new lifer.
        assertEquals(SeasonStats(species = 3, lifers = 2, outings = 2), homeState(logs, today, zone).season)
    }

    @Test
    fun `a recent outing shows its place, date, length and number of species`() {
        val logs = listOf(log(LocalDate.of(2027, 4, 17), "Made-up Heron", "Sample Sparrow", place = "Sample Marsh", minutes = 72))
        assertEquals(
            listOf(OutingRow(logs[0].outing.id, "Sample Marsh", LocalDate.of(2027, 4, 17), Duration.ofMinutes(72), species = 2)),
            homeState(logs, today, zone).recentOutings,
        )
    }

    @Test
    fun `Home lists the five most recent outings, last year's included`() {
        val logs = (0L until 7L).map { log(LocalDate.of(2027, 1, 3).minusDays(it)) }
        val state = homeState(logs, today, zone)
        assertEquals(logs.take(5).map { it.outing.id }, state.recentOutings.map { it.id })
        assertEquals(3, state.season.outings)
    }

    // A made-up table: the codes have the real shape and name no real state.
    private val table = CountyTableParser.parse("US-ZA\tAlder\nUS-ZA-001\tHeron\nUS-ZA-003\tLark Hollow\n")

    @Test
    fun `the region is the home county's name and its state's abbreviation`() {
        assertEquals(HomeRegion(county = "Lark Hollow", stateAbbreviation = "ZA"), homeRegion("US-ZA-003", table))
    }

    @Test
    fun `no home county, or one the table lacks, gives no region`() {
        assertNull(homeRegion(null, table))
        assertNull(homeRegion("US-ZZ-001", table))
    }

    @Test
    fun `the region goes into Home's state beside the figures`() {
        val region = HomeRegion(county = "Heron", stateAbbreviation = "ZA")
        val state = homeState(listOf(log(LocalDate.of(2027, 3, 14), "Made-up Heron")), today, zone, region)
        assertEquals(region, state.region)
        assertEquals(1, state.season.outings)
    }

    @Test
    fun `the view model reads finished outings from the database and the region from the settings`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder<FlywayDatabase>(context).build()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val file = File(context.filesDir, "home_test.preferences_pb").apply { delete() }
            val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            // The table that ships has no made-up county, so this one is real: the first of the bundled table.
            val counties = BundledCounties(context)
            val firstState = counties.table().states.first()
            val home = firstState.counties.first()
            settings.setHomeCountyCode(home.code)
            val clock = Clock.fixed(Instant.parse("2027-04-18T18:00:00Z"), zone)
            val outings = OutingRepository(db.outings(), clock)
            val finished = outings.start("Test Pond", "US-ZZ-001")
            outings.addOrBump(finished.id, "Test Warbler")
            outings.addOrBump(finished.id, "Test Warbler")
            outings.addOrBump(finished.id, "Sample Sparrow")
            outings.end()
            // An outing in progress is not counted until it ends.
            val inProgress = outings.start("Sample Marsh", "US-ZZ-001")
            outings.addOrBump(inProgress.id, "Made-up Heron")

            val state = withTimeout(10_000) {
                HomeViewModel(
                    outings,
                    settings,
                    counties,
                    PackRepository(File(context.filesDir, "home_test_packs")),
                    FakeCountyLocator(),
                    clock,
                    sync(db, settings, clock, account = false),
                    RecordingScheduler(),
                )
                    .state.first { it.recentOutings.isNotEmpty() }
            }

            assertEquals(HomeRegion(county = home.name, stateAbbreviation = firstState.abbreviation), state.region)

            assertEquals(SeasonStats(species = 2, lifers = 2, outings = 1), state.season)
            assertEquals(listOf(OutingRow(finished.id, "Test Pond", today, Duration.ZERO, species = 2)), state.recentOutings)
            assertTrue(state.outingInProgress)
        } finally {
            stopViewModels()
            scope.cancel()
            db.close()
        }
    }

    /**
     * Lets the view models' figures stop, as they do [HomeViewModel.STOP_AFTER_MILLIS] after Home is left, so
     * none reads the database once it is closed.
     */
    private fun stopViewModels() {
        scheduler.advanceTimeBy(HomeViewModel.STOP_AFTER_MILLIS + 1)
        scheduler.runCurrent()
    }

    /** Records what Home asks of the background sync (#43). */
    private class RecordingScheduler : SyncScheduler {
        var syncedNow = 0

        override fun sendSoon() = Unit

        override fun syncNow() {
            syncedNow++
        }

        override fun keepRefreshing(seasonRunning: Boolean) = Unit
    }

    private class MemoryAccountStore(var account: StoredAccount?) : AccountStore {
        override suspend fun read() = account

        override suspend fun write(account: StoredAccount) {
            this.account = account
        }

        override suspend fun clear() {
            account = null
        }
    }

    /** The background sync over [db], with a server that is never reached: these tests make no call. */
    private fun sync(db: FlywayDatabase, settings: SettingsRepository, clock: Clock, account: Boolean): SightingSync {
        val server = ServerClient(serverHttpClient(MockEngine { error("no call is expected") }), "https://server.example")
        val store = MemoryAccountStore(StoredAccount("00000000-0000-4000-8000-000000000001", "test-secret-not-real").takeIf { account })
        val accounts = AccountRepository(server, store, settings)
        return SightingSync(server, accounts, BracketRepository(server, accounts, db.brackets(), db.outings(), clock), db.sync(), clock)
    }

    @Test
    fun `Home says when sightings have waited more than a day, and Send now sends them`() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val db = Room.inMemoryDatabaseBuilder<FlywayDatabase>(context).build()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val file = File(context.filesDir, "home_unsent_test.preferences_pb").apply { delete() }
            val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
            // A made-up league in season since 1 March, learned the day before.
            val league = "test-league-1"
            db.brackets().upsert(BracketEntity(league, false, "2027-03-01", "UTC", picksLocked = true, picksUnsent = false, picksNotice = null))
            db.sync().saveState(SyncStateEntity(leagueId = league, leagueSince = Instant.parse("2027-03-09T08:00:00Z").toEpochMilli()))
            val clock = MovableClock(Instant.parse("2027-03-10T08:00:00Z"), zone)
            val outings = OutingRepository(db.outings(), clock)
            val outing = outings.start("Test Pond", "US-ZZ-001")
            outings.addOrBump(outing.id, "Test Warbler")
            outings.end()
            val scheduler = RecordingScheduler()
            val sync = sync(db, settings, clock, account = true)
            fun home() = HomeViewModel(outings, settings, BundledCounties(context), PackRepository(File(context.filesDir, "home_unsent_packs")), FakeCountyLocator(), clock, sync, scheduler)

            // Waiting for a day exactly: no note yet.
            clock.now = Instant.parse("2027-03-11T08:00:00Z")
            assertFalse(withTimeout(10_000) { home().state.first { it.recentOutings.isNotEmpty() } }.sightingsUnsent)

            clock.now = Instant.parse("2027-03-11T09:00:00Z")
            val later = home()
            assertTrue(withTimeout(10_000) { later.state.first { it.recentOutings.isNotEmpty() } }.sightingsUnsent)
            later.onSendNow()
            assertEquals(1, scheduler.syncedNow)
        } finally {
            stopViewModels()
            scope.cancel()
            db.close()
        }
    }

    /** A clock the test moves by hand. */
    private class MovableClock(var now: Instant, private val zone: ZoneId) : Clock() {
        override fun getZone(): ZoneId = zone

        override fun withZone(zone: ZoneId): Clock = MovableClock(now, zone)

        override fun instant(): Instant = now
    }

    /**
     * A database, settings and packs for one test of Start outing, closed after [block]. Unless a test says
     * otherwise, the location page was answered before and the app may not use the phone's location.
     */
    private fun starting(
        homeCountyCode: String?,
        installTestland: Boolean,
        locator: FakeCountyLocator = FakeCountyLocator(),
        locationAsked: Boolean = true,
        block: suspend (HomeViewModel, OutingRepository, SettingsRepository) -> Unit,
    ) =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val db = Room.inMemoryDatabaseBuilder<FlywayDatabase>(context).build()
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                val file = File(context.filesDir, "home_start_test.preferences_pb").apply { delete() }
                val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
                settings.setHomeCountyCode(homeCountyCode)
                if (locationAsked) settings.setLocationAsked()
                val packs = PackRepository(File(context.filesDir, "home_start_test_packs").apply { deleteRecursively() })
                if (installTestland) packs.install(StatePackCodec.encode(testStatePack()))
                val clock = Clock.fixed(Instant.parse("2027-04-18T18:00:00Z"), zone)
                val outings = OutingRepository(db.outings(), clock)
                val home = HomeViewModel(outings, settings, BundledCounties(context), packs, locator, clock, sync(db, settings, clock, account = false), RecordingScheduler())
                withTimeout(10_000) { block(home, outings, settings) }
            } finally {
                stopViewModels()
                scope.cancel()
                db.close()
            }
        }

    @Test
    fun `Start outing starts one in the home county when its pack is installed, and opens it`() =
        starting(homeCountyCode = "XX-TS-002", installTestland = true) { home, outings, _ ->
            home.onStartOuting()
            assertEquals(OpenOuting(fallback = null), home.openOuting.first())
            val started = outings.inProgress().filterNotNull().first()
            assertEquals("XX-TS-002", started.countyCode)
            assertEquals("", started.placeName)
            assertTrue(home.state.first { it.outingInProgress }.outingInProgress)
        }

    @Test
    fun `Start outing with an outing in progress opens that one and starts none`() =
        starting(homeCountyCode = "XX-TS-002", installTestland = false) { home, outings, _ ->
            val running = outings.start("Test Pond", "XX-TS-001")
            home.onStartOuting()
            home.openOuting.first()
            assertEquals(running, outings.inProgress().first())
        }

    @Test
    fun `Start outing without the home county's pack starts nothing and says so until dismissed`() =
        starting(homeCountyCode = "XX-TS-002", installTestland = false) { home, outings, _ ->
            home.onStartOuting()
            assertTrue(home.state.first { it.packMissing }.packMissing)
            assertNull(outings.inProgress().first())
            home.onPackMissingDismissed()
            assertFalse(home.state.first { !it.packMissing }.packMissing)
        }

    @Test
    fun `Start outing with no home county starts nothing`() =
        starting(homeCountyCode = null, installTestland = true) { home, outings, _ ->
            home.onStartOuting()
            assertTrue(home.state.first { it.packMissing }.packMissing)
            assertNull(outings.inProgress().first())
        }

    // Finding the county from the phone's location (#27). Testland's counties are made up; the home county
    // is East Square, XX-TS-002.

    @Test
    fun `the first start shows the location page and starts nothing yet`() {
        val locator = FakeCountyLocator(permitted = false)
        starting(homeCountyCode = "XX-TS-002", installTestland = true, locator = locator, locationAsked = false) { home, outings, settings ->
            home.onStartOuting()
            assertTrue(home.state.first { it.askLocation }.askLocation)
            assertNull(outings.inProgress().first())
            assertFalse(settings.locationAsked().first())
            assertEquals(0, locator.calls)
        }
    }

    @Test
    fun `Back from the location page starts nothing, and the page comes back next time`() =
        starting(homeCountyCode = "XX-TS-002", installTestland = true, locationAsked = false) { home, outings, settings ->
            home.onStartOuting()
            home.state.first { it.askLocation }
            home.onLocationCancelled()
            home.state.first { !it.askLocation }
            assertNull(outings.inProgress().first())
            assertFalse(settings.locationAsked().first())
            home.onStartOuting()
            assertTrue(home.state.first { it.askLocation }.askLocation)
        }

    @Test
    fun `denied, the outing starts in the home county with no notice, and the page is not shown again`() {
        val locator = FakeCountyLocator(permitted = false, fix = CountyFix.Found("XX-TS-001"))
        starting(homeCountyCode = "XX-TS-002", installTestland = true, locator = locator, locationAsked = false) { home, outings, settings ->
            home.onStartOuting()
            home.state.first { it.askLocation }
            home.onLocationAnswered()
            assertEquals(OpenOuting(fallback = null), home.openOuting.first())
            assertEquals("XX-TS-002", outings.inProgress().filterNotNull().first().countyCode)
            assertTrue(settings.locationAsked().first())
            assertFalse(home.state.first { !it.askLocation }.askLocation)
            assertEquals(0, locator.calls)
        }
    }

    @Test
    fun `granted and found, the outing starts in the county the phone is in`() {
        val locator = FakeCountyLocator(permitted = false, fix = CountyFix.Found("XX-TS-003"))
        starting(homeCountyCode = "XX-TS-002", installTestland = true, locator = locator, locationAsked = false) { home, outings, _ ->
            home.onStartOuting()
            home.state.first { it.askLocation }
            // The player says yes at Android's prompt.
            locator.permitted = true
            home.onLocationAnswered()
            assertEquals(OpenOuting(fallback = null), home.openOuting.first())
            assertEquals("XX-TS-003", outings.inProgress().filterNotNull().first().countyCode)
            assertEquals(1, locator.calls)
        }
    }

    @Test
    fun `granted before, the page is skipped and the county the phone is in is used`() {
        val locator = FakeCountyLocator(permitted = true, fix = CountyFix.Found("XX-TS-001"))
        starting(homeCountyCode = "XX-TS-002", installTestland = true, locator = locator, locationAsked = false) { home, outings, _ ->
            home.onStartOuting()
            assertEquals(OpenOuting(fallback = null), home.openOuting.first())
            assertEquals("XX-TS-001", outings.inProgress().filterNotNull().first().countyCode)
            assertFalse(home.state.value.askLocation)
        }
    }

    @Test
    fun `granted and outside every pack, the outing starts in the home county and says why`() {
        val locator = FakeCountyLocator(permitted = true, fix = CountyFix.OutsidePacks)
        starting(homeCountyCode = "XX-TS-002", installTestland = true, locator = locator) { home, outings, _ ->
            home.onStartOuting()
            assertEquals(OpenOuting(fallback = CountyFallback.OUTSIDE_PACKS), home.openOuting.first())
            assertEquals("XX-TS-002", outings.inProgress().filterNotNull().first().countyCode)
        }
    }

    @Test
    fun `granted and no location in time, the outing starts in the home county and says why`() {
        // The phone never answers: the outing waits LOCATE_TIMEOUT_MILLIS, on the test clock, then falls back.
        val locator = FakeCountyLocator(permitted = true, fix = null)
        starting(homeCountyCode = "XX-TS-002", installTestland = true, locator = locator) { home, outings, _ ->
            home.onStartOuting()
            locator.asked.await()
            assertTrue(home.state.first { it.findingCounty }.findingCounty)
            assertNull(outings.inProgress().first())
            scheduler.advanceTimeBy(HomeViewModel.LOCATE_TIMEOUT_MILLIS + 1)
            assertEquals(OpenOuting(fallback = CountyFallback.NO_LOCATION), home.openOuting.first())
            assertEquals("XX-TS-002", outings.inProgress().filterNotNull().first().countyCode)
            assertFalse(home.state.first { !it.findingCounty }.findingCounty)
        }
    }

    @Test
    fun `found while the home county's pack is missing, the outing still starts where the phone is`() {
        val locator = FakeCountyLocator(permitted = true, fix = CountyFix.Found("XX-TS-001"))
        starting(homeCountyCode = "US-ZZ-009", installTestland = true, locator = locator) { home, outings, _ ->
            home.onStartOuting()
            assertEquals(OpenOuting(fallback = null), home.openOuting.first())
            assertEquals("XX-TS-001", outings.inProgress().filterNotNull().first().countyCode)
        }
    }

    @Test
    fun `no location and no pack for the home county, nothing starts and Home says so`() {
        val locator = FakeCountyLocator(permitted = true, fix = CountyFix.NoLocation)
        starting(homeCountyCode = "US-ZZ-009", installTestland = true, locator = locator) { home, outings, _ ->
            home.onStartOuting()
            assertTrue(home.state.first { it.packMissing }.packMissing)
            assertNull(outings.inProgress().first())
        }
    }
}
