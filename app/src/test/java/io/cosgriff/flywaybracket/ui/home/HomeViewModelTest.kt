package io.cosgriff.flywaybracket.ui.home

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.data.db.FlywayDatabase
import io.cosgriff.flywaybracket.domain.outing.Outing
import io.cosgriff.flywaybracket.domain.outing.OutingLog
import io.cosgriff.flywaybracket.domain.outing.Sighting
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.testStatePack
import io.cosgriff.flywaybracket.domain.place.CountyTableParser
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

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
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
                HomeViewModel(outings, settings, counties, PackRepository(File(context.filesDir, "home_test_packs")), clock)
                    .state.first { it.recentOutings.isNotEmpty() }
            }

            assertEquals(HomeRegion(county = home.name, stateAbbreviation = firstState.abbreviation), state.region)

            assertEquals(SeasonStats(species = 2, lifers = 2, outings = 1), state.season)
            assertEquals(listOf(OutingRow(finished.id, "Test Pond", today, Duration.ZERO, species = 2)), state.recentOutings)
            assertTrue(state.outingInProgress)
        } finally {
            scope.cancel()
            db.close()
        }
    }

    /** A database, settings and packs for one test of Start outing, closed after [block]. */
    private fun starting(homeCountyCode: String?, installTestland: Boolean, block: suspend (HomeViewModel, OutingRepository) -> Unit) =
        runBlocking {
            val context: Context = ApplicationProvider.getApplicationContext()
            val db = Room.inMemoryDatabaseBuilder<FlywayDatabase>(context).build()
            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            try {
                val file = File(context.filesDir, "home_start_test.preferences_pb").apply { delete() }
                val settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
                settings.setHomeCountyCode(homeCountyCode)
                val packs = PackRepository(File(context.filesDir, "home_start_test_packs").apply { deleteRecursively() })
                if (installTestland) packs.install(StatePackCodec.encode(testStatePack()))
                val clock = Clock.fixed(Instant.parse("2027-04-18T18:00:00Z"), zone)
                val outings = OutingRepository(db.outings(), clock)
                withTimeout(10_000) { block(HomeViewModel(outings, settings, BundledCounties(context), packs, clock), outings) }
            } finally {
                scope.cancel()
                db.close()
            }
        }

    @Test
    fun `Start outing starts one in the home county when its pack is installed, and opens it`() =
        starting(homeCountyCode = "XX-TS-002", installTestland = true) { home, outings ->
            home.onStartOuting()
            home.openOuting.first()
            val started = outings.inProgress().filterNotNull().first()
            assertEquals("XX-TS-002", started.countyCode)
            assertEquals("", started.placeName)
            assertTrue(home.state.first { it.outingInProgress }.outingInProgress)
        }

    @Test
    fun `Start outing with an outing in progress opens that one and starts none`() =
        starting(homeCountyCode = "XX-TS-002", installTestland = false) { home, outings ->
            val running = outings.start("Test Pond", "XX-TS-001")
            home.onStartOuting()
            home.openOuting.first()
            assertEquals(running, outings.inProgress().first())
        }

    @Test
    fun `Start outing without the home county's pack starts nothing and says so until dismissed`() =
        starting(homeCountyCode = "XX-TS-002", installTestland = false) { home, outings ->
            home.onStartOuting()
            assertTrue(home.state.first { it.packMissing }.packMissing)
            assertNull(outings.inProgress().first())
            home.onPackMissingDismissed()
            assertFalse(home.state.first { !it.packMissing }.packMissing)
        }

    @Test
    fun `Start outing with no home county starts nothing`() =
        starting(homeCountyCode = null, installTestland = true) { home, outings ->
            home.onStartOuting()
            assertTrue(home.state.first { it.packMissing }.packMissing)
            assertNull(outings.inProgress().first())
        }
}
