package io.cosgriff.flywaybracket.ui.outing

import android.content.Context
import androidx.compose.runtime.snapshots.Snapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.room3.Room
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.OutingRepository
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.data.db.FlywayDatabase
import io.cosgriff.flywaybracket.domain.outing.Outing
import io.cosgriff.flywaybracket.domain.outing.OutingLog
import io.cosgriff.flywaybracket.domain.outing.Sighting
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.TimeBin
import io.cosgriff.flywaybracket.domain.pack.chart
import io.cosgriff.flywaybracket.domain.pack.resident
import io.cosgriff.flywaybracket.domain.pack.testStatePack
import io.cosgriff.flywaybracket.domain.place.CountyTableParser
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Places, county codes, bird names, figures and times are made up.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OutingViewModelTest {
    private val zone = ZoneId.of("America/Denver")
    private val start: Instant = LocalDate.of(2027, 5, 9).atTime(7, 0).atZone(zone).toInstant()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val db = Room.inMemoryDatabaseBuilder<FlywayDatabase>(context).build()
    private val clock = Clock.fixed(start.plus(Duration.ofMinutes(42)), zone)
    // Outings start at [start]; the screens read [clock], 42 minutes later.
    private val outings = OutingRepository(db.outings(), Clock.fixed(start, zone))
    private val counties = BundledCounties(context)

    // A made-up table: the codes have the real shape and name no real state.
    private val table = CountyTableParser.parse("US-ZA\tAlder\nUS-ZA-003\tLark Hollow\n")

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    /** The view models a test made, stopped before [db] closes. */
    private val viewModels = mutableListOf<ViewModel>()

    private fun <T : ViewModel> kept(viewModel: T): T = viewModel.also { viewModels += it }

    @After
    fun close() {
        // A view model's flows keep reading the database for a while after the test's last look at them. Cancel
        // them and wait until they have stopped, then close the database: a query left running would fail on
        // the closed pool, and kotlinx-coroutines-test would report it in whichever test runs a coroutine next.
        runBlocking { viewModels.forEach { it.viewModelScope.coroutineContext.job.cancelAndJoin() } }
        Dispatchers.resetMain()
        db.close()
    }

    /** No packs on the phone. */
    private fun packs() = PackRepository(File(context.filesDir, "outing_test_no_packs").apply { deleteRecursively() })

    private fun outing(endedAt: Instant? = null) = Outing(7, "Test Pond", "US-ZA-003", start, endedAt)

    private fun sighting(id: Long, bird: String, count: Int, minutes: Long) =
        Sighting(id, 7, bird, count, start.plus(Duration.ofMinutes(minutes)))

    @Test
    fun `the outing lists the latest bird first, with local times, and names the county`() {
        val state = outingState(
            outing(),
            listOf(sighting(1, "Test Warbler", 3, minutes = 5), sighting(2, "Sample Sparrow", 1, minutes = 20)),
            table,
            zone,
        )
        assertEquals("Test Pond", state.place)
        assertEquals(OutingCounty("Lark Hollow", "ZA"), state.county)
        assertEquals(
            listOf(SightingRow(2, "Sample Sparrow", 1, LocalTime.of(7, 20)), SightingRow(1, "Test Warbler", 3, LocalTime.of(7, 5))),
            state.sightings,
        )
        assertEquals(2, state.species)
        assertEquals(4, state.birds)
    }

    @Test
    fun `a county the table lacks has no name`() {
        assertNull(outingState(outing().copy(countyCode = "US-ZZ-009"), emptyList(), table, zone).county)
    }

    @Test
    fun `the time since the start never goes below zero`() {
        assertEquals(Duration.ofMinutes(42), elapsedSince(outing(), clock))
        assertEquals(Duration.ZERO, elapsedSince(outing(), Clock.fixed(start.minusSeconds(30), zone)))
    }

    @Test
    fun `the sheet lists the pack's birds in order, with how many are logged`() {
        val county = OutingCounty("Lark Hollow", "ZA")
        val chart = chart(resident("Test Warbler", 0.2), resident("Sample Sparrow", 0.5), resident("Rare Test Rail", 0.01))
        val state = addSightingState(chart, TimeBin.of(5, 2), "", listOf(sighting(1, "Test Warbler", 3, minutes = 5)), county)
        assertEquals(
            listOf(
                BirdRow("Sample Sparrow", expected = true),
                BirdRow("Test Warbler", expected = true, logged = 3),
                BirdRow("Rare Test Rail", expected = false),
            ),
            state.birds,
        )
        assertEquals(county, state.county)
        assertEquals(listOf("Test Warbler"), addSightingState(chart, TimeBin.of(5, 2), "warb", emptyList(), county).birds.map { it.name })
    }

    @Test
    fun `with no pack for the county the sheet says so and lists nothing`() {
        val state = addSightingState(null, TimeBin.of(5, 2), "", emptyList(), null)
        assertEquals(AddSightingState(hasPack = false), state)
    }

    @Test
    fun `the summary has the place, the day, the times, the length and the birds in the order logged`() {
        val log = OutingLog(
            outing(endedAt = start.plus(Duration.ofMinutes(72))),
            listOf(sighting(1, "Test Warbler", 3, minutes = 5), sighting(2, "Sample Sparrow", 1, minutes = 20)),
        )
        assertEquals(
            SummaryState(
                place = "Test Pond",
                date = LocalDate.of(2027, 5, 9),
                start = LocalTime.of(7, 0),
                end = LocalTime.of(8, 12),
                length = Duration.ofMinutes(72),
                sightings = listOf(SummaryRow("Test Warbler", 3), SummaryRow("Sample Sparrow", 1)),
            ),
            summaryState(log, zone),
        )
    }

    @Test
    fun `the view model shows the outing in progress and changes it`() = runBlocking {
        val id = outings.start("Test Pond", "US-ZZ-001").id
        outings.addOrBump(id, "Test Warbler")
        val outing = kept(OutingViewModel(outings, counties, packs(), clock))
        withTimeout(10_000) {
            val shown = outing.state.first { it.sightings.isNotEmpty() }.sightings.single()
            assertEquals(Duration.ofMinutes(42), outing.elapsed.first { it > Duration.ZERO })

            outing.onOneMore(shown)
            val two = outing.state.first { it.sightings.single().count == 2 }.sightings.single()
            outing.onOneFewer(two)
            val one = outing.state.first { it.sightings.single().count == 1 }.sightings.single()
            // At one, minus does nothing: the screen asks, then removes.
            outing.onOneFewer(one)
            outing.onRename(" Sample Marsh ")
            assertEquals("Sample Marsh", outing.state.first { it.place == "Sample Marsh" }.place)
            assertEquals(listOf(1), outings.sightings(id).first().map { it.count })

            outing.onRemove(one)
            outing.state.first { it.sightings.isEmpty() }
            assertEquals(emptyList<Sighting>(), outings.sightings(id).first())
        }
    }

    @Test
    fun `finishing ends the outing and leads to its summary`() = runBlocking {
        val id = outings.start("Test Pond", "US-ZZ-001").id
        val outing = kept(OutingViewModel(outings, counties, packs(), clock))
        withTimeout(10_000) {
            outing.onFinish()
            assertEquals(OutingExit.Finished(id), outing.exit.first())
        }
        assertNull(outings.inProgress().first())
        assertEquals(listOf(id), outings.finished().first().map { it.id })
    }

    @Test
    fun `discarding deletes the outing and closes the screen`() = runBlocking {
        outings.start("Test Pond", "US-ZZ-001")
        val outing = kept(OutingViewModel(outings, counties, packs(), clock))
        withTimeout(10_000) {
            outing.onDiscard()
            assertEquals(OutingExit.Closed, outing.exit.first())
        }
        assertNull(outings.inProgress().first())
        assertEquals(emptyList<Outing>(), outings.finished().first())
    }

    @Test
    fun `with no outing in progress the screen closes`() = runBlocking {
        val outing = kept(OutingViewModel(outings, counties, packs(), clock))
        assertEquals(OutingExit.Closed, withTimeout(10_000) { outing.exit.first() })
    }

    @Test
    fun `the county picker lists each pack's counties in alphabetical order`() {
        assertEquals(
            listOf(
                StateCounties(
                    "Testland",
                    listOf(CountyChoice("XX-TS-002", "East Square"), CountyChoice("XX-TS-003", "Twin Islands"), CountyChoice("XX-TS-001", "West Square")),
                ),
            ),
            countyChoices(listOf(testStatePack())),
        )
        assertEquals(emptyList<StateCounties>(), countyChoices(emptyList()))
    }

    @Test
    fun `the outing state carries the county's code for the picker`() {
        assertEquals("US-ZA-003", outingState(outing(), emptyList(), table, zone).countyCode)
    }

    @Test
    fun `changing the county moves the outing in progress and lists the installed packs' counties`() = runBlocking {
        val packs = PackRepository(File(context.filesDir, "outing_county_test_packs").apply { deleteRecursively() })
        packs.install(StatePackCodec.encode(testStatePack()))
        val id = outings.start("", "XX-TS-002").id
        val outing = kept(OutingViewModel(outings, counties, packs, clock))
        withTimeout(10_000) {
            val choices = outing.countyChoices.first { it.isNotEmpty() }
            assertEquals(listOf("East Square", "Twin Islands", "West Square"), choices.single().counties.map { it.name })
            assertEquals("XX-TS-002", outing.state.first { it.countyCode.isNotEmpty() }.countyCode)
            outing.onChangeCounty("XX-TS-003")
            assertEquals("XX-TS-003", outing.state.first { it.countyCode == "XX-TS-003" }.countyCode)
        }
        assertEquals(id, outings.inProgress().first()?.id)
        assertEquals("XX-TS-003", outings.inProgress().first()?.countyCode)
    }

    @Test
    fun `picking a bird logs it on the outing in progress and clears the search`() = runBlocking {
        val packs = PackRepository(File(context.filesDir, "outing_test_packs").apply { deleteRecursively() })
        packs.install(StatePackCodec.encode(testStatePack()))
        // West Square has a warbler from May and a sparrow all year; the clock is in May.
        val id = outings.start("", "XX-TS-001").id
        val adding = kept(AddSightingViewModel(outings, packs, counties, clock))
        withTimeout(10_000) {
            assertEquals(listOf("Test Sparrow", "Test Warbler"), adding.state.first { it.birds.isNotEmpty() }.birds.map { it.name })
            adding.onQuery("warb")
            Snapshot.sendApplyNotifications()
            assertEquals(listOf("Test Warbler"), adding.state.first { it.birds.size == 1 }.birds.map { it.name })
            adding.onPick("Test Warbler")
            assertEquals("", adding.query)
            outings.sightings(id).first { it.isNotEmpty() }
            adding.onPick("Test Warbler")
            assertEquals(listOf("Test Warbler" to 2), outings.sightings(id).first { it.single().count == 2 }.map { it.birdName to it.count })
        }
    }
}
