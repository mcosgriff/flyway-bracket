package io.cosgriff.flywaybracket.ui.packs

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import io.cosgriff.flywaybracket.data.BundledCounties
import io.cosgriff.flywaybracket.data.InstalledPack
import io.cosgriff.flywaybracket.data.PackRepository
import io.cosgriff.flywaybracket.data.SettingsRepository
import io.cosgriff.flywaybracket.domain.pack.PackState
import io.cosgriff.flywaybracket.domain.pack.StatePackCodec
import io.cosgriff.flywaybracket.domain.pack.StatePackResult
import io.cosgriff.flywaybracket.domain.pack.county
import io.cosgriff.flywaybracket.domain.pack.rectangle
import io.cosgriff.flywaybracket.domain.pack.resident
import io.cosgriff.flywaybracket.domain.pack.testStatePack
import io.cosgriff.flywaybracket.domain.place.CountyTableParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Every state, county and figure is made up: Testland from the shared fixture, and a second state beside it.
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PacksViewModelTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val directory = File(context.filesDir, "packs_test").apply { deleteRecursively() }
    private val packs = PackRepository(directory)
    private val settings = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = scope) { File(context.filesDir, "packs_test.preferences_pb").apply { delete() } },
    )

    private val testland = testStatePack()
    private val testlandBytes = StatePackCodec.encode(testland)
    private val testlandInstalled = InstalledPack(testland, sizeBytes = 2_048)
    private val testlandRow = PackRow("XX-TS", "Testland", counties = 3, LocalDate.of(2026, 3, 14), 2_048, "Made-up figures for tests")

    private val otherland = testland.copy(
        state = PackState("XX-OT", "Otherland"),
        counties = listOf(county("XX-OT-001", "Far Square", listOf(resident("Test Sparrow", 0.5)), rectangle(10.0, 0.0, 11.0, 1.0))),
    )
    private val otherlandInstalled = InstalledPack(otherland, sizeBytes = 512)
    private val otherlandRow = testlandRow.copy(stateCode = "XX-OT", state = "Otherland", counties = 1, sizeBytes = 512)

    // A made-up table: the codes have the real shape and name no real state.
    private val table = CountyTableParser.parse("US-ZA\tAlder\nUS-ZA-001\tHeron\nUS-ZA-003\tLark Hollow\n")

    @Before
    fun setMain() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun close() {
        Dispatchers.resetMain()
        scope.cancel()
    }

    @Test
    fun `no packs and no home county give the empty state`() {
        assertEquals(PacksState(), packsState(emptyList(), homeCountyCode = null, table))
    }

    @Test
    fun `with no pack for it, the home county is named from the bundled table and has no species figure`() {
        val state = packsState(listOf(testlandInstalled), "US-ZA-003", table)
        assertEquals(HomeCountyLine(county = "Lark Hollow", state = "Alder", species = null), state.home)
        assertNull(state.homePack)
        assertEquals(listOf(testlandRow), state.extraPacks)
    }

    @Test
    fun `a home county that nothing names is left out`() {
        assertNull(packsState(listOf(testlandInstalled), "US-ZZ-001", table).home)
    }

    @Test
    fun `the pack that holds the home county is set apart, and the others are extra`() {
        val state = packsState(listOf(otherlandInstalled, testlandInstalled), "XX-TS-002", table)
        assertEquals(testlandRow, state.homePack)
        assertEquals(listOf(otherlandRow), state.extraPacks)
        assertEquals(listOf(testlandRow, otherlandRow), state.packs)
    }

    @Test
    fun `the home county's species are the birds expected there at some time of year`() {
        // East Square has a sandpiper expected in September only and a sparrow expected all year.
        val state = packsState(listOf(testlandInstalled), "XX-TS-002", table)
        assertEquals(HomeCountyLine(county = "East Square", state = "Testland", species = 2), state.home)
    }

    @Test
    fun `a bird never on 3 percent of checklists is not one of the home county's species`() {
        val rare = testland.copy(
            counties = listOf(
                county(
                    "XX-TS-001",
                    "West Square",
                    listOf(resident("Test Sparrow", 0.6), resident("Test Vagrant", 0.004)),
                    rectangle(0.0, 0.0, 1.0, 1.0),
                ),
            ),
        )
        assertEquals(1, packsState(listOf(InstalledPack(rare, 1)), "XX-TS-001", table).home?.species)
    }

    @Test
    fun `each reason a pack file is rejected has plain words to show`() {
        assertEquals(PackProblem.NOT_A_PACK, packProblem(StatePackResult.Reason.NOT_GZIP))
        assertEquals(PackProblem.NOT_A_PACK, packProblem(StatePackResult.Reason.NOT_JSON))
        assertEquals(PackProblem.DAMAGED, packProblem(StatePackResult.Reason.MISSING_FIELD))
        assertEquals(PackProblem.DAMAGED, packProblem(StatePackResult.Reason.BAD_BIN_COUNT))
        assertEquals(PackProblem.TOO_NEW, packProblem(StatePackResult.Reason.TOO_NEW))
    }

    @Test
    fun `only the pack with the home county in it holds it`() {
        val installed = listOf(otherlandInstalled, testlandInstalled)
        assertTrue(holds(installed, "XX-TS", "XX-TS-002"))
        assertFalse(holds(installed, "XX-OT", "XX-TS-002"))
        assertFalse(holds(installed, "XX-TS", null))
    }

    private fun viewModel() = PacksViewModel(packs, settings, BundledCounties(context), context)

    /** A file the system's file picker could hand back. */
    private fun file(name: String, content: InputStream): Uri =
        Uri.parse("content://io.cosgriff.flywaybracket.test/$name").also { shadowOf(context.contentResolver).registerInputStream(it, content) }

    private suspend fun PacksViewModel.awaitState(matches: (PacksState) -> Boolean): PacksState =
        withTimeout(10_000) { state.first(matches) }

    @Test
    fun `a chosen pack file is installed and listed`() = runBlocking {
        settings.setHomeCountyCode("XX-TS-001")
        val viewModel = viewModel()

        viewModel.onFileChosen(file("XX-TS.fbpack", testlandBytes.inputStream()))

        val state = viewModel.awaitState { it.homePack != null }
        assertEquals(testlandRow.copy(sizeBytes = testlandBytes.size.toLong()), state.homePack)
        assertEquals(HomeCountyLine(county = "West Square", state = "Testland", species = 2), state.home)
        assertNull(state.problem)
    }

    @Test
    fun `a chosen file that is not a pack is not installed, and the state says why`() = runBlocking {
        val viewModel = viewModel()

        viewModel.onFileChosen(file("notes.txt", "not a pack".byteInputStream()))

        assertEquals(PackProblem.NOT_A_PACK, viewModel.awaitState { it.problem != null }.problem)
        assertEquals(emptyList<InstalledPack>(), packs.installed().first())
    }

    @Test
    fun `a file larger than any pack is turned away before it is read whole`() = runBlocking {
        val viewModel = viewModel()
        val endless = object : InputStream() {
            override fun read(): Int = 0
        }

        viewModel.onFileChosen(file("film.fbpack", endless))

        assertEquals(PackProblem.TOO_LARGE, viewModel.awaitState { it.problem != null }.problem)
    }

    @Test
    fun `a file that cannot be read is reported`() = runBlocking {
        val viewModel = viewModel()
        val broken = object : InputStream() {
            override fun read(): Int = throw IOException("made to fail")
        }

        viewModel.onFileChosen(file("gone.fbpack", broken))

        assertEquals(PackProblem.NOT_READ, viewModel.awaitState { it.problem != null }.problem)
    }

    @Test
    fun `adding a pack clears the reason the file before it was turned away`() = runBlocking {
        val viewModel = viewModel()
        viewModel.onFileChosen(file("notes.txt", "not a pack".byteInputStream()))
        viewModel.awaitState { it.problem != null }

        viewModel.onFileChosen(file("XX-TS.fbpack", testlandBytes.inputStream()))

        // The pack is listed a moment before the reason goes, so wait for both.
        val state = viewModel.awaitState { it.extraPacks.isNotEmpty() && it.problem == null }
        assertEquals(listOf("XX-TS"), state.extraPacks.map { it.stateCode })
    }

    @Test
    fun `an extra pack is removed, and the pack that holds the home county is not`() = runBlocking {
        settings.setHomeCountyCode("XX-TS-001")
        packs.install(testlandBytes)
        packs.install(StatePackCodec.encode(otherland))
        val viewModel = viewModel()
        viewModel.awaitState { it.extraPacks.isNotEmpty() }

        viewModel.onRemove("XX-TS")
        viewModel.onRemove("XX-OT")

        val state = viewModel.awaitState { it.extraPacks.isEmpty() }
        assertEquals("XX-TS", state.homePack?.stateCode)
        assertEquals(listOf("XX-TS"), packs.installed().first().map { it.pack.state.code })
    }
}
